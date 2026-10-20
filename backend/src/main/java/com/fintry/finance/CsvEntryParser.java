package com.fintry.finance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounded CSV reader for the documented Fintry import format. */
final class CsvEntryParser {
  static final int MAX_BYTES = 1_048_576;
  static final int MAX_ROWS = 500;
  static final Set<String> REQUIRED =
      Set.of("descricao", "tipo", "valor", "vencimento", "categoria");
  static final Set<String> ALLOWED =
      Set.of("descricao", "tipo", "valor", "vencimento", "categoria", "situacao", "data_pagamento");

  record Line(int number, Map<String, String> fields) {}

  static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  static List<Line> parse(String csv) {
    if (csv == null || csv.isBlank())
      throw invalid("Selecione um CSV com cabeçalho e lançamentos.");
    if (csv.length() > MAX_BYTES || csv.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw invalid("O CSV deve ter até 1 MB.");
    if (csv.indexOf('\u0000') >= 0 || csv.indexOf('\ufffd') >= 0)
      throw invalid("Salve o arquivo como CSV UTF-8 antes de importar.");
    if (csv.startsWith("\ufeff")) csv = csv.substring(1);
    String header = csv.split("\\r?\\n", 2)[0];
    char delimiter = header.indexOf(';') >= 0 ? ';' : ',';
    var records = records(csv, delimiter);
    if (records.size() < 2) throw invalid("O CSV precisa de pelo menos um lançamento.");
    var headers = records.get(0).stream().map(CsvEntryParser::key).toList();
    if (new HashSet<>(headers).size() != headers.size())
      throw invalid("O cabeçalho contém colunas repetidas.");
    if (!headers.containsAll(REQUIRED) || !ALLOWED.containsAll(headers))
      throw invalid(
          "Use as colunas descricao, tipo, valor, vencimento e categoria; opcionais: situacao e"
              + " data_pagamento. Baixe o modelo para conferir.");
    var result = new ArrayList<Line>();
    for (int i = 1; i < records.size(); i++) {
      var values = records.get(i);
      if (values.size() != headers.size())
        throw invalid(
            "Registro "
                + (i + 1)
                + ": quantidade de colunas diferente do cabeçalho. Confira o separador e as"
                + " aspas.");
      var fields = new HashMap<String, String>();
      for (int j = 0; j < headers.size(); j++) fields.put(headers.get(j), values.get(j).strip());
      result.add(new Line(i + 1, fields));
    }
    return result;
  }

  private static List<List<String>> records(String csv, char delimiter) {
    var rows = new ArrayList<List<String>>();
    var row = new ArrayList<String>();
    var field = new StringBuilder();
    boolean quoted = false, closed = false;
    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
            closed = true;
          }
        } else field.append(c);
      } else if (c == delimiter || c == '\r' || c == '\n') {
        row.add(field.toString());
        field.setLength(0);
        closed = false;
        if (c != delimiter) {
          if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(List.copyOf(row));
          row.clear();
          if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
        }
      } else if (c == '"') {
        if (field.length() != 0 || closed)
          throw invalid(
              "Aspas inválidas no CSV. Use o modelo e coloque o campo inteiro entre aspas.");
        quoted = true;
      } else {
        if (closed) throw invalid("Há texto após o fechamento das aspas no CSV.");
        field.append(c);
      }
      if (row.size() > 7) throw invalid("O CSV contém colunas demais. Confira o separador.");
      if (rows.size() > MAX_ROWS + 1) throw invalid("Importe até 500 lançamentos por arquivo.");
    }
    if (quoted) throw invalid("O CSV contém aspas sem fechamento.");
    row.add(field.toString());
    if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(List.copyOf(row));
    if (rows.size() > MAX_ROWS + 1) throw invalid("Importe até 500 lançamentos por arquivo.");
    return rows;
  }

  static String key(String value) {
    return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace(' ', '_');
  }

  static LocalDate date(String value) {
    try {
      LocalDate date;
      if (value.matches("\\d{4}-\\d{2}-\\d{2}")) date = LocalDate.parse(value);
      else if (value.matches("\\d{2}/\\d{2}/\\d{4}"))
        date =
            LocalDate.parse(
                value,
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
      else throw new DateTimeException("format");
      if (date.getYear() < 1900 || date.getYear() > 2100) throw new DateTimeException("range");
      return date;
    } catch (DateTimeException ex) {
      throw new IllegalArgumentException(
          "Use uma data válida entre 1900 e 2100, em DD/MM/AAAA ou AAAA-MM-DD.");
    }
  }

  static BigDecimal amount(String value) {
    if (value.length() > 32)
      throw new IllegalArgumentException("O valor informado excede o limite permitido.");
    String normalized;
    if (value.matches("(?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{1,2}"))
      normalized = value.replace(".", "").replace(',', '.');
    else if (value.matches("\\d+(?:\\.\\d{1,2})?")) normalized = value;
    else
      throw new IllegalArgumentException(
          "Valor inválido. Use 1234,56 ou 1234.56, sem sinal ou símbolo de moeda.");
    var amount = new BigDecimal(normalized);
    if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("999999999999.99")) > 0)
      throw new IllegalArgumentException("O valor deve ser de 0,01 até 999999999999,99.");
    return amount.setScale(2);
  }
}
