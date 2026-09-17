from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'hragentv1/backend/src/test/java/com/hragent/hragentv1/service/EmployeeAgentRouterTest.java'
t=p.read_text(encoding='utf-8-sig').replace('new EmployeeAgentRouter(relations,services,handbook,policies)', 'new EmployeeAgentRouter(relations,services,handbook,policies,mock(PersonalAssistantService.class))')
p.write_text(t,encoding='utf-8')
p=root/'.artifacts/Dockerfile.personal'
p.parent.mkdir(exist_ok=True)
dockerfile=(root/'hragentv1/backend/Dockerfile').read_text(encoding='utf-8-sig')
dockerfile=dockerfile.replace('FROM eclipse-temurin:21-jre', 'RUN mvn -q -Dtest=PersonalAssistantServiceTest,EmployeeAgentRouterTest test\n\nFROM eclipse-temurin:21-jre')
p.write_text(dockerfile,encoding='utf-8')
