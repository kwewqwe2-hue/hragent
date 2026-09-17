package com.hragent.hragentv1.service;

import org.apache.poi.xwpf.usermodel.*;
import java.util.*;
import java.util.regex.*;
import java.util.function.Consumer;

/** Reads document fields as data, including split Word runs, tables, headers and footers. */
public final class CertificateTemplateFields {
    private CertificateTemplateFields() {}
    public static final Map<String,String> LABELS = Map.ofEntries(
        Map.entry("legalName","姓名"), Map.entry("englishName","英文姓名"), Map.entry("employeeNo","工号"),
        Map.entry("department","部门"), Map.entry("title","职位"), Map.entry("entryDate","入职日期"),
        Map.entry("passportNumber","护照号码"), Map.entry("passportExpiryDate","护照有效期"),
        Map.entry("monthlySalary","月薪"), Map.entry("currency","币种"), Map.entry("companyName","公司名称"),
        Map.entry("issueDate","开具日期"), Map.entry("purpose","用途"), Map.entry("destinationCountry","目的国家"),
        Map.entry("consulateName","受理机构"));
    private static final Pattern FIELD = Pattern.compile("\\{\\{([^{}\\r\\n]{1,60})}}|【([^【】\\r\\n]{1,60})】|\\$\\{([^{}\\r\\n]{1,60})}");
    public static String key(String label) {
        String s=label.trim();
        for(var e:LABELS.entrySet())if(e.getValue().equals(s)||e.getKey().equals(s))return e.getKey();
        return switch(s){case "员工姓名","员工名称"->"legalName";case "岗位","职务"->"title";case "入职时间"->"entryDate";case "月收入","税前月薪"->"monthlySalary";case "企业名称","单位名称"->"companyName";case "日期","出具日期"->"issueDate";case "证明用途"->"purpose";default->s;};
    }
    public static List<String> normalize(XWPFDocument doc) {
        tableFields(doc.getBodyElements());
        doc.getHeaderList().forEach(h->tableFields(h.getBodyElements()));
        doc.getFooterList().forEach(f->tableFields(f.getBodyElements()));
        Set<String> fields=new LinkedHashSet<>();
        paragraphs(doc,p->{
            // Only labelled blanks are inferred; an unlabeled underline is not guessed.
            for(var e:LABELS.entrySet()) {
                var m=Pattern.compile(Pattern.quote(e.getValue())+"[：:]?\\s*[_＿]{2,}").matcher(p.getText());
                var matches=new ArrayList<String>();while(m.find())matches.add(m.group());
                for(String match:matches)replace(p,match,e.getValue()+"：{{"+e.getKey()+"}}");
            }
            var m=FIELD.matcher(p.getText());var replacements=new LinkedHashMap<String,String>();
            while(m.find()){String raw=m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3);String k=key(raw);fields.add(k);replacements.put(m.group(),"{{"+k+"}}");}
            replacements.forEach((a,b)->{if(!a.equals(b))replace(p,a,b);});
        });
        return List.copyOf(fields);
    }
    private static void tableFields(List<IBodyElement> elements){
        for(var element:elements)if(element instanceof XWPFTable table)for(var row:table.getRows()){
            var cells=row.getTableCells();
            for(int i=0;i+1<cells.size();i++){
                String label=cells.get(i).getText().trim(),k=key(label.replaceAll("[：:]$",""));
                if(!label.isEmpty()&&label.length()<=30&&(LABELS.containsKey(k)||label.endsWith("：")||label.endsWith(":"))&&cells.get(i+1).getText().trim().matches("[_＿\\s]*")){
                    var target=cells.get(i+1);var p=target.getParagraphs().isEmpty()?target.addParagraph():target.getParagraphs().getFirst();
                    if(p.getText().isBlank())p.createRun().setText("{{"+k+"}}");else replace(p,p.getText(),"{{"+k+"}}");
                }
            }
            cells.forEach(c->tableFields(c.getBodyElements()));
        }
    }
    public static void sealAnchor(XWPFDocument doc) {
        final boolean[] found={false};
        paragraphs(doc,p->{if(p.getText().contains("公司盖章处"))found[0]=true;});
        if(found[0])return;
        paragraphs(doc,p->{if(!found[0])for(String marker:List.of("（公章）","（盖章）","单位盖章处","公司盖章"))if(p.getText().contains(marker)){replace(p,marker,"公司盖章处");found[0]=true;break;}});
        if(!found[0]){var p=doc.createParagraph();p.setAlignment(ParagraphAlignment.RIGHT);p.setSpacingBefore(500);p.setSpacingAfter(500);var run=p.createRun();run.setText("公司盖章处");run.setFontSize(12);}
    }
    public static void paragraphs(XWPFDocument doc,Consumer<XWPFParagraph> action){
        body(doc.getBodyElements(),action);doc.getHeaderList().forEach(h->body(h.getBodyElements(),action));doc.getFooterList().forEach(f->body(f.getBodyElements(),action));
    }
    private static void body(List<IBodyElement> elements,Consumer<XWPFParagraph> action){
        for(var e:elements)if(e instanceof XWPFParagraph p)action.accept(p);else if(e instanceof XWPFTable t)t.getRows().forEach(r->r.getTableCells().forEach(c->body(c.getBodyElements(),action)));
    }
    public static void replace(XWPFParagraph p,String needle,String value){
        String original=p.getRuns().stream().map(XWPFRun::text).reduce("",String::concat);
        var offsets=new ArrayList<Integer>();for(int i=original.indexOf(needle);i>=0;i=original.indexOf(needle,i+needle.length()))offsets.add(i);
        Collections.reverse(offsets);
        for(int start:offsets){int end=start+needle.length(),cursor=0,first=-1,last=-1,so=0,eo=0;var runs=p.getRuns();
            for(int i=0;i<runs.size();i++){int next=cursor+runs.get(i).text().length();if(first<0&&start<next){first=i;so=start-cursor;}if(end<=next){last=i;eo=end-cursor;break;}cursor=next;}
            if(first<0||last<0)throw new IllegalStateException("模板字段结构无法解析");
            String prefix=runs.get(first).text().substring(0,so),suffix=runs.get(last).text().substring(eo);
            set(runs.get(first),prefix+Objects.requireNonNullElse(value,"")+(first==last?suffix:""));
            for(int i=first+1;i<last;i++)set(runs.get(i),"");if(first!=last)set(runs.get(last),suffix);
        }
    }
    private static void set(XWPFRun r,String s){while(r.getCTR().sizeOfTArray()>0)r.getCTR().removeT(0);r.setText(s);}
}
