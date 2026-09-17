from pathlib import Path
root=Path(__file__).resolve().parents[1]
t=(root/'hragentv1/backend/Dockerfile').read_text(encoding='utf-8-sig')
t=t.replace('FROM eclipse-temurin:21-jre','RUN mvn -q -Dtest=PolicyCatalogTest,EmployeeSelfServiceAssistantTest,EmployeeAgentRouterTest,PersonalAssistantServiceTest test\n\nFROM eclipse-temurin:21-jre')
(root/'.artifacts').mkdir(exist_ok=True)
(root/'.artifacts/Dockerfile.catalog').write_text(t,encoding='utf-8')
