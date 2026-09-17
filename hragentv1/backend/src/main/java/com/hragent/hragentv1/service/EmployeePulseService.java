package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.ErPulseResponseRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.*;
import java.util.*;

@Service
public class EmployeePulseService {
    public static final List<String> KEYS=List.of("clarity","workload","communication","support","tools");
    public static final List<String> TITLES=List.of("我清楚当前工作的目标和优先级","工作量与可用时间基本匹配","我能与主管坦诚沟通工作安排","需要帮助时，我能获得同事或组织的支持","现有工具和流程能帮助我完成工作");
    private final ErPulseResponseRepository repository;
    private final SecretCryptoService crypto;
    public EmployeePulseService(ErPulseResponseRepository repository,SecretCryptoService crypto){this.repository=repository;this.crypto=crypto;}
    static String currentPeriod(){return YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();}
    private String participant(UserAccount u,String period){return crypto.fingerprint("er-pulse:"+u.getTenantId()+":"+period+":"+u.getId());}
    public Map<String,Object> current(UserAccount u){
        String p=currentPeriod();
        return Map.of("period",p,"submitted",repository.existsByTenantIdAndPeriodAndParticipant(u.getTenantId(),p,participant(u,p)),"questions",questions());
    }
    public List<Map<String,String>> questions(){
        List<Map<String,String>> result=new ArrayList<>();
        for(int i=0;i<KEYS.size();i++)result.add(Map.of("name",KEYS.get(i),"title",TITLES.get(i)));
        return result;
    }
    public void submit(UserAccount u,String period,boolean consent,Map<String,Integer> answers){
        if(!consent)throw AppException.badRequest("请先确认自愿参与");
        if(!currentPeriod().equals(period))throw AppException.badRequest("本期已结束，请刷新后填写新一期反馈");
        if(answers==null || !answers.keySet().equals(new HashSet<>(KEYS)) || answers.values().stream().anyMatch(v->v==null||v<1||v>5))
            throw AppException.badRequest("请为全部五项选择 1 至 5 分");
        String marker=participant(u,period);
        if(repository.existsByTenantIdAndPeriodAndParticipant(u.getTenantId(),period,marker))throw AppException.badRequest("本期已提交，请勿重复提交");
        ErPulseResponse row=new ErPulseResponse();row.setTenantId(u.getTenantId());row.setPeriod(period);row.setParticipant(marker);
        row.setEncryptedAnswers(crypto.encrypt(String.join(",",KEYS.stream().map(k->answers.get(k).toString()).toList())));
        try{repository.saveAndFlush(row);}catch(DataIntegrityViolationException ex){throw AppException.badRequest("本期已提交，请勿重复提交");}
    }
    public Map<String,Object> results(UserAccount u,String period){
        if(u.getRole()!=Role.HR)throw AppException.forbidden("仅 HR 可查看反馈汇总");
        YearMonth selected;
        try{selected=YearMonth.parse(period);}catch(Exception ex){throw AppException.badRequest("月份格式应为 YYYY-MM");}
        // Only closed, immutable cohorts are exposed: no live differencing or per-department slices.
        if(!selected.isBefore(YearMonth.parse(currentPeriod())))return Map.of("period",period,"available",false,"message","本期收集中；结束后且至少 5 人参与时展示汇总。","metrics",List.of());
        var rows=repository.findByTenantIdAndPeriod(u.getTenantId(),period);
        if(rows.size()<5)return Map.of("period",period,"available",false,"message","参与人数未达到 5 人，暂不展示人数和评分。","metrics",List.of());
        double[] sums=new double[KEYS.size()];
        for(var row:rows){String[] values=crypto.decrypt(row.getEncryptedAnswers()).split(",");for(int i=0;i<sums.length;i++)sums[i]+=Integer.parseInt(values[i]);}
        List<Map<String,Object>> metrics=new ArrayList<>();
        for(int i=0;i<sums.length;i++)metrics.add(Map.of("title",TITLES.get(i),"average",Math.round(sums[i]/rows.size()*10)/10.0));
        return Map.of("period",period,"available",true,"participants",rows.size(),"message","评分反映自愿参与者的体验，不能代表所有员工；建议结合团队沟通确定改进措施。","metrics",metrics);
    }
}
