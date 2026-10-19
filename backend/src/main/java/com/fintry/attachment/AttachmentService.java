package com.fintry.attachment;

import com.fintry.auth.CurrentUser;
import com.fintry.finance.EntryRepository;
import java.io.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {
  public static final int MAX_BYTES = 5 * 1024 * 1024, MAX_FILES = 10;
  private final CurrentUser current;
  private final com.fintry.audit.AuditService audit;
  private final EntryRepository entries;
  private final AttachmentRepository attachments;

  public record View(
      UUID id,
      String filename,
      String contentType,
      long byteCount,
      String uploadedByName,
      LocalDateTime createdAt) {}

  public record Download(String filename, String contentType, byte[] content) {}

  private ResponseStatusException missing() {
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Lançamento ou comprovante não encontrado.");
  }

  private ResponseStatusException invalid(String text) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
  }

  private void entry(UUID id) {
    if (entries.findByIdAndCompanyId(id, current.companyId()).isEmpty()) throw missing();
  }

  private View view(EntryAttachment a) {
    return new View(
        a.getId(),
        a.getFilename(),
        a.getContentType(),
        a.getByteCount(),
        a.getUploadedByName(),
        a.getCreatedAt());
  }

  public List<View> list(UUID entryId) {
    entry(entryId);
    return attachments
        .findByCompanyIdAndEntryIdOrderByCreatedAtDescIdDesc(current.companyId(), entryId)
        .stream()
        .map(
            a ->
                new View(
                    a.getId(),
                    a.getFilename(),
                    a.getContentType(),
                    a.getByteCount(),
                    a.getUploadedByName(),
                    a.getCreatedAt()))
        .toList();
  }

  private String filename(String raw) {
    if (raw == null) throw invalid("Escolha um arquivo PDF, PNG ou JPEG.");
    String name = Normalizer.normalize(raw.replace('\\', '/'), Normalizer.Form.NFC);
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
    if (name.isBlank() || name.length() > 120)
      throw invalid("Use um nome de arquivo de até 120 caracteres.");
    return name;
  }

  private String validate(String name, byte[] data) {
    String lower = name.toLowerCase(Locale.ROOT);
    boolean pdf = lower.endsWith(".pdf"),
        png = lower.endsWith(".png"),
        jpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    if (!pdf && !png && !jpg) throw invalid("Envie somente arquivos PDF, PNG ou JPEG.");
    try {
      if (pdf) {
        if (data.length < 5
            || !new String(data, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
          throw invalid("O conteúdo não corresponde a um PDF válido.");
        try (var document = Loader.loadPDF(data)) {
          if (document.isEncrypted() || document.getNumberOfPages() < 1)
            throw invalid("Envie um PDF válido, sem senha.");
        }
        return "application/pdf";
      }
      try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw invalid("A imagem não é válida.");
        var reader = readers.next();
        try {
          reader.setInput(stream, true, true);
          String format = reader.getFormatName();
          if (png && !format.equalsIgnoreCase("PNG") || jpg && !format.equalsIgnoreCase("JPEG"))
            throw invalid("A extensão não corresponde à imagem enviada.");
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || (long) width * height > 20_000_000)
            throw invalid("Use uma imagem de até 20 megapixels.");
          if (reader.read(0) == null) throw invalid("A imagem não é válida.");
        } finally {
          reader.dispose();
        }
      }
      return png ? "image/png" : "image/jpeg";
    } catch (IOException | IllegalArgumentException ex) {
      throw invalid(
          "Arquivo inválido, corrompido ou protegido por senha. Envie outro PDF, PNG ou JPEG.");
    }
  }

  @Transactional
  public View upload(UUID entryId, MultipartFile file) {
    var user = current.get();
    entries.lock(entryId, user.getCompanyId()).orElseThrow(this::missing);
    if (file.isEmpty()) throw invalid("O arquivo está vazio.");
    if (file.getSize() > MAX_BYTES)
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Cada comprovante pode ter até 5 MB.");
    if (attachments.countByCompanyIdAndEntryId(user.getCompanyId(), entryId) >= MAX_FILES)
      throw invalid(
          "Cada lançamento aceita até 10 comprovantes. Remova um arquivo para enviar outro.");
    String name = filename(file.getOriginalFilename());
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException ex) {
      throw invalid("Não foi possível ler o arquivo. Tente novamente.");
    }
    String type = validate(name, bytes);
    var a = new EntryAttachment();
    a.setCompanyId(user.getCompanyId());
    a.setEntryId(entryId);
    a.setFilename(name);
    a.setContentType(type);
    a.setByteCount(bytes.length);
    a.setContent(bytes);
    a.setUploadedBy(user.getId());
    a.setUploadedByName(user.getName());
    attachments.saveAndFlush(a);
    audit.change(
        "ATTACH_ADD",
        "ENTRY",
        entryId,
        name,
        Map.of(),
        com.fintry.audit.AuditService.fields("Arquivo", name, "Tipo", type, "Bytes", bytes.length));
    return view(a);
  }

  public Download download(UUID entryId, UUID id) {
    var a =
        attachments
            .findByIdAndEntryIdAndCompanyId(id, entryId, current.companyId())
            .orElseThrow(this::missing);
    return new Download(a.getFilename(), a.getContentType(), a.getContent());
  }

  @Transactional
  public void delete(UUID entryId, UUID id) {
    entries.lock(entryId, current.companyId()).orElseThrow(this::missing);
    var a =
        attachments
            .findByIdAndEntryIdAndCompanyId(id, entryId, current.companyId())
            .orElseThrow(this::missing);
    attachments.delete(a);
    audit.change(
        "ATTACH_REMOVE",
        "ENTRY",
        entryId,
        a.getFilename(),
        com.fintry.audit.AuditService.fields("Arquivo", a.getFilename(), "Bytes", a.getByteCount()),
        Map.of());
  }
}
