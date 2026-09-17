import java.time.LocalDate;
import java.util.jar.JarFile;

/** Read-only smoke probe against the actual packaged parser; creates no employee drafts. */
class ProbeLeaveDates {
 public static void main(String[] args) throws Exception {
  try(var jar=new JarFile(args[0])){
   var loader=new ClassLoader(ProbeLeaveDates.class.getClassLoader()){
    protected Class<?> findClass(String name)throws ClassNotFoundException{
     try{var entry=jar.getJarEntry("BOOT-INF/classes/"+name.replace('.','/')+".class");
      if(entry==null)throw new ClassNotFoundException(name);
      byte[] bytes=jar.getInputStream(entry).readAllBytes();return defineClass(name,bytes,0,bytes.length);
     }catch(java.io.IOException e){throw new ClassNotFoundException(name,e);}
    }
   };
   var parser=loader.loadClass("com.hragent.hragentv1.service.LeaveDateParser");
   var parse=parser.getDeclaredMethod("parse",String.class,LocalDate.class,LocalDate.class);parse.setAccessible(true);
   int failed=0;
   for(String text:new String[]{"915-917","0915-0917","915","26.9.15-9.17","26.9.15","9/15","9/15-9/17","26/9/15-9/17","26.12.30-1.2"}){
    try{
     var result=parse.invoke(null,text,LocalDate.of(2026,9,10),null);
     var start=result.getClass().getDeclaredMethod("start");start.setAccessible(true);
     var end=result.getClass().getDeclaredMethod("end");end.setAccessible(true);
     var confirm=result.getClass().getDeclaredMethod("confirmation");confirm.setAccessible(true);
     boolean crossYear=text.contains("12.30");
     LocalDate expectedStart=LocalDate.parse(crossYear?"2026-12-30":"2026-09-15");
     LocalDate expectedEnd=crossYear?LocalDate.parse("2027-01-02"):text.contains("-")?LocalDate.parse("2026-09-17"):null;
     if(!expectedStart.equals(start.invoke(result))||!java.util.Objects.equals(expectedEnd,end.invoke(result))||!Boolean.TRUE.equals(confirm.invoke(result)))throw new IllegalStateException("Unexpected normalized dates: "+result);
     System.out.println(text+" -> PASS: "+result);
    }
    catch(java.lang.reflect.InvocationTargetException e){failed++;System.out.println(text+" -> FAIL: "+e.getCause().getMessage());}
   }
   if(failed>0)throw new IllegalStateException(failed+" packaged-parser checks failed");
  }
 }
}
