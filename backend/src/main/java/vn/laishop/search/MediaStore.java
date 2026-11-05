package vn.laishop.search;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.laishop.common.ApiException;

@Component
public class MediaStore {
    private final Path root;
    public MediaStore(@Value("${app.media-root}") String root) throws IOException { this.root=Path.of(root).toAbsolutePath().normalize(); Files.createDirectories(this.root); }
    public String save(byte[] bytes) throws IOException { String name=UUID.randomUUID().toString(); Files.write(path(name),bytes,StandardOpenOption.CREATE_NEW); return name; }
    private Path path(String name) { if(!name.matches("[a-f0-9-]{36}")) throw ApiException.missing(); Path result=root.resolve(name).normalize(); if(!result.getParent().equals(root)) throw ApiException.missing(); return result; }
    public byte[] read(String name) throws IOException { return Files.readAllBytes(path(name)); }
    public void delete(String name) throws IOException { Files.deleteIfExists(path(name)); }
    public Path root() { return root; }
}
