from pathlib import Path
root=Path(__file__).resolve().parents[1]
t=(root/'hragentv1/backend/Dockerfile').read_text(encoding='utf-8-sig')
t=t.replace('FROM eclipse-temurin:21-jre','RUN mvn -q -Dtest=MaternityPolicyTest,PolicyCopilotServiceTest,PersonalAssistantServiceTest,EmployeeAgentRouterTest test\n\nFROM eclipse-temurin:21-jre')
(root/'.artifacts/Dockerfile.maternity').write_text(t,encoding='utf-8')
