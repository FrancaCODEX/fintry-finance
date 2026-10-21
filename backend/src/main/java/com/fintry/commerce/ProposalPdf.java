package com.fintry.commerce;

import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import java.io.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProposalPdf {
  private final CurrentUser current;
  private final CompanyRepository companies;

  private String clean(String text, PDFont font) throws IOException {
    var out = new StringBuilder();
    for (int cp : Objects.toString(text, "").codePoints().toArray()) {
      String s = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
      try {
        font.encode(s);
        out.append(s);
      } catch (IllegalArgumentException ex) {
        out.append('?');
      }
    }
    return out.toString();
  }

  private void text(PDPageContentStream c, PDFont font, int size, float x, float y, String text)
      throws IOException {
    c.beginText();
    c.setFont(font, size);
    c.newLineAtOffset(x, y);
    c.showText(clean(text, font));
    c.endText();
  }

  private List<String> wrap(String raw, PDFont font, int size, float width) throws IOException {
    String value = clean(raw, font);
    var result = new ArrayList<String>();
    var line = new StringBuilder();
    for (int cp : value.codePoints().toArray()) {
      String ch = new String(Character.toChars(cp));
      if (font.getStringWidth(line + ch) / 1000 * size > width && line.length() > 0) {
        result.add(line.toString());
        line.setLength(0);
      }
      line.append(ch);
    }
    if (line.length() > 0) result.add(line.toString());
    return result;
  }

  private String money(BigDecimal value) {
    return "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", value);
  }

  public byte[] export(ProposalService.View p) throws IOException {
    var company = companies.findById(current.companyId()).orElseThrow();
    var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    var blocks = new ArrayList<List<String>>();
    String status =
        switch (p.status()) {
          case DRAFT -> "Rascunho";
          case OPEN -> "Em negociação";
          case APPROVED -> "Aprovada";
          case REJECTED -> "Recusada";
          case CANCELLED -> "Cancelada";
        };
    blocks.add(wrap(p.title(), bold, 14, 500));
    blocks.add(
        wrap(
            "Cliente: " + Objects.toString(p.contactName(), "Consumidor final"), regular, 10, 500));
    blocks.add(
        List.of(
            "Validade: "
                + p.validUntil().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " | "
                + status
                + (p.expired() ? " | Validade expirada" : "")));
    for (var i : p.items()) {
      var row = new ArrayList<String>();
      row.addAll(wrap(i.sku() + " · " + i.productName(), bold, 10, 500));
      row.addAll(
          wrap(
              (i.kind() == Product.Kind.SERVICE ? "Serviço" : "Produto")
                  + " | "
                  + i.quantity().stripTrailingZeros().toPlainString()
                  + " "
                  + i.unit()
                  + " x "
                  + money(i.unitPrice())
                  + " = "
                  + money(i.total()),
              regular,
              10,
              500));
      blocks.add(row);
    }
    blocks.add(List.of("TOTAL: " + money(p.total())));
    if (p.notes() != null && !p.notes().isBlank())
      blocks.add(wrap("Observações: " + p.notes(), regular, 10, 500));
    blocks.add(
        List.of(
            "A aprovação é registrada pela equipe no Fintry.",
            "Proposta comercial. Não é nota fiscal nem comprovante de pagamento."));
    try (var doc = new PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      PDPageContentStream content = null;
      float y = 0;
      int page = 0;
      try {
        for (var block : blocks) {
          if (content == null || y - block.size() * 16 - 12 < 65) {
            if (content != null) content.close();
            var sheet = new PDPage(PDRectangle.A4);
            doc.addPage(sheet);
            content = new PDPageContentStream(doc, sheet);
            page++;
            content.setNonStrokingColor(16 / 255f, 46 / 255f, 39 / 255f);
            content.addRect(0, 752, 595, 90);
            content.fill();
            content.setNonStrokingColor(1f, 1f, 1f);
            text(content, bold, 20, 40, 805, "fintry. | Proposta #" + p.number());
            var names =
                wrap(
                    Objects.toString(company.getTradeName(), company.getLegalName()),
                    regular,
                    10,
                    500);
            for (int n = 0; n < Math.min(2, names.size()); n++)
              text(content, regular, 10, 40, 782 - n * 12, names.get(n));
            content.setNonStrokingColor(0f, 0f, 0f);
            text(content, regular, 8, 40, 35, "Proposta #" + p.number() + " | Página " + page);
            y = 725;
          }
          for (var line : block) {
            text(content, regular, 10, 40, y, line);
            y -= 16;
          }
          y -= 12;
        }
      } finally {
        if (content != null) content.close();
      }
      doc.save(bytes);
      return bytes.toByteArray();
    }
  }
}
