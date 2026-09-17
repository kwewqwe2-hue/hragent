package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Bounded same-host official HTML discovery; unknown formats fail visibly rather than becoming policy. */
@Service
public class OfficialPolicyCrawler {
 public record Source(String id,String name,String url,String region){}
 public static final List<Source> SOURCES=List.of(
  new Source("shanghai","上海人社·规范性文件","https://rsj.sh.gov.cn/tgwgfx_17726/index.html","上海"),
  new Source("beijing","北京人社·政策文件","https://rsj.beijing.gov.cn/zzcwj/","北京"),
  new Source("ministry","人力资源社会保障部·政策文件","https://www.mohrss.gov.cn/wap/zc/zcwj/","全国"),
  new Source("national","中国政府网·部门政策文件","https://www.gov.cn/zhengce/zhengceku/bmwj/home.htm","全国"),
  new Source("finance","财政部会计司·政策发布","https://kjs.mof.gov.cn/zhengcefabu/","全国")
 );
 private final OfficialPolicySourceStateRepository states;private final TenantRepository tenants;private final OfficialPolicyIngestService ingest;
 public record Progress(String phase,String source,String document,int sourcesFinished,int sourcesTotal,int documentsChecked,int documentsTotal,int changedDocuments,String startedAt,String finishedAt){}
 private volatile Progress progress=new Progress("IDLE","","",0,SOURCES.size(),0,0,0,"","");
 private void report(String phase,String source,String document,int finished,int checked,int total,int changed,String end){
  progress=new Progress(phase,source,document,finished,SOURCES.size(),checked,total,changed,progress.startedAt(),end);
 }
 @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
 public void catchUp(){if(enabled)trigger();}
 private final AtomicBoolean running=new AtomicBoolean();private final ExecutorService worker=Executors.newSingleThreadExecutor();private final boolean enabled;
 public OfficialPolicyCrawler(OfficialPolicySourceStateRepository states,TenantRepository tenants,OfficialPolicyIngestService ingest,@Value("${app.policy-crawler.enabled:true}") boolean enabled){this.states=states;this.tenants=tenants;this.ingest=ingest;this.enabled=enabled;}
 @Scheduled(cron="${app.policy-crawler.cron:0 0 8,18 * * *}",zone="Asia/Shanghai") public void scheduled(){if(enabled)trigger();}
 @PreDestroy public void shutdown(){worker.shutdownNow();}
 public boolean trigger(){if(!running.compareAndSet(false,true))return false;
  progress=new Progress("DISCOVERING","","",0,SOURCES.size(),0,0,0,LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString(),"");
  worker.submit(()->{try{for(var source:SOURCES){if(Thread.currentThread().isInterrupted())break;scan(source);report("DISCOVERING","","",progress.sourcesFinished()+1,progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),"");}}
   finally{report("FINISHED","","",progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString());running.set(false);}});return true;}
 public Map<String,Object> status(){return Map.of("enabled",enabled,"running",running.get(),"schedule","每天 08:00、18:00（北京时间）","sources",SOURCES,"checks",states.findAll(),"progress",progress,"asOf",LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),"scope","检查配置栏目首页最近的相关 HTML 文件（最多 20 篇），并轮询复查已收录文件（每次最多 20 篇）；附件、跨站链接和历史库全量内容需人工核验。");}
 private void scan(Source source){report("DISCOVERING",source.name(),"",progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),"");var state=states.findById(source.id).orElseGet(OfficialPolicySourceState::new);state.id=source.id;state.name=source.name;state.url=source.url;state.checkedAt=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));state.documentsChecked=0;state.error=null;
  var errors=new ArrayList<String>();
  try {
   String origin=URI.create(source.url).resolve("/").toString();var robotsResponse=Jsoup.connect(origin+"robots.txt").userAgent("HRAgentPolicyMonitor/1.0").timeout(12000).maxBodySize(200000).followRedirects(false).ignoreHttpErrors(true).execute();
   boolean missingRobots=robotsResponse.statusCode()==404;
   if(mofMissingRobotsRedirect(source.url,robotsResponse.statusCode(),robotsResponse.header("Location"))){
    var missing=fetch("https://www.mof.gov.cn/404.htm");
    missingRobots=missing.text().contains("您访问的页面不存在或已删除");
   }
   if(robotsResponse.statusCode()!=200&&!missingRobots)throw new IllegalStateException("robots.txt 暂不可确认，暂停该来源抓取");
   String robots=robotsResponse.statusCode()==200?robotsResponse.body():"";
   if(!permitted(robots,URI.create(source.url).getPath()))throw new IllegalStateException("网站抓取规则不允许访问该栏目");
   var index=fetch(source.url);var links=new LinkedHashMap<String,String>();
   for(var a:index.select("a[href]")){String url=a.absUrl("href").split("#")[0];String title=a.attr("title").isBlank()?a.text():a.attr("title");
    if(sameHost(source.url,url)&&url.matches(".*(?:/20\\d{2}[^?]*|/content_\\d+).*\\.(?:html|htm|shtml)$")&&relevant(title))links.putIfAbsent(url,title);
   }
   if(links.isEmpty())throw new IllegalStateException("未识别到相关政策链接，可能栏目已改版或需要动态加载；不能判定没有更新");
   var active=tenants.findAll().stream().filter(Tenant::isActive).toList();
   var targets=new LinkedHashMap<String,String>();links.entrySet().stream().limit(20).forEach(e->targets.put(e.getKey(),e.getValue()));
   var watched=ingest.watched(source.name).entrySet().stream().filter(e->sameHost(source.url,e.getKey())).toList();
   if(!watched.isEmpty()){int count=Math.min(20,watched.size());for(int i=0;i<count;i++){var e=watched.get(Math.floorMod(state.recheckOffset+i,watched.size()));targets.putIfAbsent(e.getKey(),e.getValue());}state.recheckOffset=Math.floorMod(state.recheckOffset+count,watched.size());}
   report("READING",source.name(),"",progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal()+targets.size(),progress.changedDocuments(),"");
   for(var entry:targets.entrySet()){
    report("READING",source.name(),entry.getValue(),progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),"");
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    try{if(!permitted(robots,URI.create(entry.getKey()).getPath()))throw new IllegalStateException("抓取规则不允许访问正文");
     var doc=fetch(entry.getKey());var body=doc.selectFirst(".TRS_Editor, #mainTextZoom, #UCAP-CONTENT, #UCAP-CONTENT1, .pages_content, .article-content");
     if(body==null)throw new IllegalStateException("未识别正文，需人工核验");
     body.select("script,style,iframe").remove();String content=body.wholeText().replaceAll("[\\t\\x0B\\f\\r ]+"," ").replaceAll("\\n{3,}","\n\n").trim();
     if(content.length()<100||content.length()>18000)throw new IllegalStateException("正文过短或超过自动采集上限，需人工导入");
     String title=meta(doc,"ArticleTitle");if(title.isBlank())title=entry.getValue();if(title.length()>240||entry.getKey().length()>320)throw new IllegalStateException("标题或地址超过字段上限");
     LocalDate published=parseDate(meta(doc,"PubDate"));if(published==null)published=parseDate(meta(doc,"publishdate"));
     LocalDate effective=effectiveDate(content);
     boolean changed=false;
     for(var tenant:active)changed|=ingest.ingest(tenant.getId(),source.name,entry.getKey(),source.region,title,content,published,effective);
     if(changed)report("READING",source.name(),title,progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments()+1,"");
     state.documentsChecked++;Thread.sleep(800);
    }catch(InterruptedException e){Thread.currentThread().interrupt();errors.add("检查被中断，请重新检查");break;}catch(Exception e){errors.add(entry.getValue()+"："+e.getMessage());}finally{report("READING",source.name(),entry.getValue(),progress.sourcesFinished(),progress.documentsChecked()+1,progress.documentsTotal(),progress.changedDocuments(),"");}
   }
   if(errors.isEmpty()&&state.documentsChecked>0)state.successfulAt=state.checkedAt;
  }catch(Exception e){errors.add(e.getMessage());}
  if(!errors.isEmpty()){String message=String.join("；",errors);state.error=message.substring(0,Math.min(1000,message.length()));}states.save(state);
 }
 private Document fetch(String url)throws Exception {var response=Jsoup.connect(url).userAgent("HRAgentPolicyMonitor/1.0").timeout(15000).maxBodySize(2000000).followRedirects(false).execute();if(response.statusCode()!=200)throw new IllegalStateException("HTTP "+response.statusCode());if(response.bodyAsBytes().length>=2000000)throw new IllegalStateException("页面超过大小上限，未完整采集");return response.parse();}
 static boolean sameHost(String base,String url){try{var u=URI.create(url);return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&URI.create(base).getHost().equals(u.getHost());}catch(Exception e){return false;}}
 static boolean mofMissingRobotsRedirect(String source,int status,String location){return "kjs.mof.gov.cn".equals(URI.create(source).getHost())&&status==302&&"http://www.mof.gov.cn/404.htm".equals(location);}
 static boolean relevant(String title){return title!=null&&title.length()>10&&Pattern.compile("劳动|工资|薪酬|社保|社会保险|公积金|生育|产假|年休假|休假|工伤|养老|企业年金|退休|失业|缴费|就业|合同|用工|报销|电子凭证|电子会计凭证|电子发票|电子客票|行程单|财务共享|会计信息化").matcher(title).find()&&!Pattern.compile("名单|公示|招聘|解读|答记者问|征求意见稿").matcher(title).find();}
 static String meta(Document doc,String name){return doc.select("meta[name="+name+"]").attr("content");}
 static LocalDate parseDate(String value){var m=Pattern.compile("(20\\d{2})[-年/.](\\d{1,2})[-月/.](\\d{1,2})").matcher(value);if(m.find())try{return LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));}catch(Exception ignored){}return null;}
 static LocalDate effectiveDate(String content){var m=Pattern.compile("自(20\\d{2}年\\d{1,2}月\\d{1,2}日)起(?:施行|实施|执行)").matcher(content);Set<LocalDate> dates=new HashSet<>();while(m.find()){var d=parseDate(m.group(1));if(d!=null)dates.add(d);}return dates.size()==1?dates.iterator().next():null;}
 static boolean permitted(String robots,String path){
  boolean applies=false,hasRules=false;int allow=-1,deny=-1;
  for(String line:robots.split("\\R")){
   line=line.replace("\uFEFF","").split("#",2)[0].trim();int colon=line.indexOf(':');if(colon<0)continue;
   String key=line.substring(0,colon).trim().toLowerCase(Locale.ROOT),value=line.substring(colon+1).trim();
   if(key.equals("user-agent")){if(hasRules){applies=false;hasRules=false;}applies|=value.equals("*")||value.equalsIgnoreCase("HRAgentPolicyMonitor");continue;}
   if(!key.equals("allow")&&!key.equals("disallow"))continue;
   hasRules=true;if(!applies||value.isEmpty())continue;
   boolean exact=value.endsWith("$");String rule=exact?value.substring(0,value.length()-1):value;
   String regex=Arrays.stream(rule.split("\\*",-1)).map(Pattern::quote).collect(java.util.stream.Collectors.joining(".*"));
   if(path.matches(regex+(exact?"":".*"))){int weight=rule.replace("*","").length();if(key.equals("allow"))allow=Math.max(allow,weight);else deny=Math.max(deny,weight);}
  }
  return deny<0||allow>=deny;
 }
}
