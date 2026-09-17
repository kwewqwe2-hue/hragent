package com.hragent.hragentv1.service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
public record CertificateMemoryFile(String name,String contentType,byte[] bytes) implements MultipartFile {
 public String getName(){return "file";} public String getOriginalFilename(){return name;}
 public String getContentType(){return contentType;} public boolean isEmpty(){return bytes.length==0;}
 public long getSize(){return bytes.length;} public byte[] getBytes(){return bytes.clone();}
 public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
 public void transferTo(File file)throws IOException{java.nio.file.Files.write(file.toPath(),bytes);}
}
