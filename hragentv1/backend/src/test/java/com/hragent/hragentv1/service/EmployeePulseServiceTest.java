package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.ErPulseResponseRepository;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.*;
import java.time.YearMonth;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EmployeePulseServiceTest {
    ErPulseResponseRepository repo=mock(ErPulseResponseRepository.class);
    SecretCryptoService crypto=new SecretCryptoService("test-pulse-key-not-production");
    EmployeePulseService service=new EmployeePulseService(repo,crypto);
    UserAccount user=new UserAccount();
    String period=EmployeePulseService.currentPeriod();
    Map<String,Integer> answers=Map.of("clarity",4,"workload",3,"communication",5,"support",4,"tools",4);
    @BeforeEach void setup(){user.setId(8L);user.setTenantId(2L);user.setRole(Role.EMPLOYEE);}
    @Test void consentRequired(){assertThrows(AppException.class,()->service.submit(user,period,false,answers));verifyNoInteractions(repo);}
    @Test void validateValuesAndKeys(){var invalid=new HashMap<>(answers);invalid.put("tools",6);assertThrows(AppException.class,()->service.submit(user,period,true,invalid));invalid.remove("tools");assertThrows(AppException.class,()->service.submit(user,period,true,invalid));verifyNoInteractions(repo);}
    @Test void closedMonthCannotChange(){assertThrows(AppException.class,()->service.submit(user,"2020-01",true,answers));verifyNoInteractions(repo);}
    @Test void duplicateRejected(){when(repo.existsByTenantIdAndPeriodAndParticipant(eq(2L),eq(period),anyString())).thenReturn(true);assertThrows(AppException.class,()->service.submit(user,period,true,answers));verify(repo,never()).saveAndFlush(any());}
    @Test void encryptedWithTenantScopedMarker(){service.submit(user,period,true,answers);var capture=org.mockito.ArgumentCaptor.forClass(ErPulseResponse.class);verify(repo).saveAndFlush(capture.capture());var row=capture.getValue();assertEquals(2L,row.getTenantId());assertEquals(64,row.getParticipant().length());assertNotEquals("4,3,5,4,4",row.getEncryptedAnswers());assertEquals("4,3,5,4,4",crypto.decrypt(row.getEncryptedAnswers()));assertNotEquals(crypto.fingerprint("er-pulse:3:"+period+":8"),row.getParticipant());}
    @Test void employeeAndManagerCannotReadResults(){assertThrows(AppException.class,()->service.results(user,"2020-01"));user.setRole(Role.MANAGER);assertThrows(AppException.class,()->service.results(user,"2020-01"));verifyNoInteractions(repo);}
    @Test void openMonthNeverExposesResults(){user.setRole(Role.HR);var result=service.results(user,period);assertEquals(false,result.get("available"));assertFalse(result.containsKey("participants"));verifyNoInteractions(repo);}
    @Test void smallCohortHidesCountsAndScores(){user.setRole(Role.HR);when(repo.findByTenantIdAndPeriod(2L,"2020-01")).thenReturn(Collections.nCopies(4,new ErPulseResponse()));var result=service.results(user,"2020-01");assertEquals(false,result.get("available"));assertFalse(result.containsKey("participants"));assertEquals(List.of(),result.get("metrics"));}
    @Test void closedCohortAggregatesOnlyOwnTenant(){user.setRole(Role.HR);var row=new ErPulseResponse();row.setEncryptedAnswers(crypto.encrypt("4,3,5,4,4"));when(repo.findByTenantIdAndPeriod(2L,"2020-01")).thenReturn(Collections.nCopies(5,row));var result=service.results(user,"2020-01");assertEquals(true,result.get("available"));assertEquals(5,result.get("participants"));assertFalse(result.toString().contains("participant="));assertTrue(result.toString().contains("average=3.0"));verify(repo).findByTenantIdAndPeriod(2L,"2020-01");}
}
