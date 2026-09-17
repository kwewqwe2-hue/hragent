from pathlib import Path
root=Path(__file__).resolve().parents[1]
def edit(path,old,new):
 p=root/path
 s=p.read_text(encoding='utf-8-sig')
 assert old in s, (path,old[:90])
 p.write_text(s.replace(old,new),encoding='utf-8')
base='hragentv1/backend/src/main/java/com/hragent/hragentv1/'
p=base+'service/OfficialPolicyCrawler.java'
edit(p,'new Source("national",','new Source("ministry","人力资源社会保障部·政策文件","https://www.mohrss.gov.cn/wap/zc/zcwj/","全国"),\n  new Source("national",')
edit(p,' private final AtomicBoolean running=', ''' public record Progress(String phase,String source,String document,int sourcesFinished,int sourcesTotal,int documentsChecked,int documentsTotal,int changedDocuments,String startedAt,String finishedAt){}
 private volatile Progress progress=new Progress("IDLE","","",0,SOURCES.size(),0,0,0,"","");
 private void report(String phase,String source,String document,int finished,int checked,int total,int changed,String end){
  progress=new Progress(phase,source,document,finished,SOURCES.size(),checked,total,changed,progress.startedAt(),end);
 }
 @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
 public void catchUp(){if(enabled)trigger();}
 private final AtomicBoolean running=''')
edit(p,'public boolean trigger(){if(!running.compareAndSet(false,true))return false;worker.submit(()->{try{for(var source:SOURCES){if(Thread.currentThread().isInterrupted())break;scan(source);}}finally{running.set(false);}});return true;}', '''public boolean trigger(){if(!running.compareAndSet(false,true))return false;
  progress=new Progress("DISCOVERING","","",0,SOURCES.size(),0,0,0,LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString(),"");
  worker.submit(()->{try{for(var source:SOURCES){if(Thread.currentThread().isInterrupted())break;scan(source);report("DISCOVERING","","",progress.sourcesFinished()+1,progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),"");}}
   finally{report("FINISHED","","",progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString());running.set(false);}});return true;}''')
edit(p,'"checks",states.findAll(),"scope",','"checks",states.findAll(),"progress",progress,"asOf",LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),"scope",')
edit(p,' private void scan(Source source){var state=', ' private void scan(Source source){report("DISCOVERING",source.name(),"",progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),"");var state=')
edit(p,'   for(var entry:targets.entrySet()){','''   report("READING",source.name(),"",progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal()+targets.size(),progress.changedDocuments(),"");
   for(var entry:targets.entrySet()){
    report("READING",source.name(),entry.getValue(),progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments(),"");''')
edit(p,'     for(var tenant:active)ingest.ingest(tenant.getId(),source.name,entry.getKey(),source.region,title,content,published,effective);','''     boolean changed=false;
     for(var tenant:active)changed|=ingest.ingest(tenant.getId(),source.name,entry.getKey(),source.region,title,content,published,effective);
     if(changed)report("READING",source.name(),title,progress.sourcesFinished(),progress.documentsChecked(),progress.documentsTotal(),progress.changedDocuments()+1,"");''')
edit(p,'errors.add(entry.getValue()+"："+e.getMessage());}', 'errors.add(entry.getValue()+"："+e.getMessage());}finally{report("READING",source.name(),entry.getValue(),progress.sourcesFinished(),progress.documentsChecked()+1,progress.documentsTotal(),progress.changedDocuments(),"");}')
edit(p,'养老|退休|失业|缴费|就业|合同|用工','养老|企业年金|退休|失业|缴费|就业|合同|用工')
edit(base+'web/PolicyMonitorController.java','    @GetMapping("/admin/policy-monitor/sources")', '''    @GetMapping("/policy-monitor/progress")
    public ApiResponse<java.util.Map<String,Object>> progress(HttpServletRequest request) {
        var actor=authService.requireLifecycleUser(request);
        var result=new java.util.LinkedHashMap<String,Object>(crawler.status());
        // Aggregate only this workspace's candidates; never expose another tenant's review data.
        var rows=policyMonitorService.list(actor.getTenantId()).stream().filter(c->c.sourceId().startsWith("official-")).toList();
        result.put("pendingCount",rows.stream().filter(c->c.reviewStatus()==com.hragent.hragentv1.domain.PolicyReviewStatus.PENDING_REVIEW).count());
        result.put("approvedCount",rows.stream().filter(c->c.reviewStatus()==com.hragent.hragentv1.domain.PolicyReviewStatus.APPROVED).count());
        return ApiResponse.ok(result);
    }
    @GetMapping("/admin/policy-monitor/sources")''')
