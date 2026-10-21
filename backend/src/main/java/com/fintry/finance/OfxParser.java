package com.fintry.finance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounded OFX banking reader. Does not execute XML, resolve entities or access the network. */
public final class OfxParser {
  private OfxParser() {}

  private static final Set<String> SCALARS =
      Set.of(
          "CURDEF",
          "BANKID",
          "BRANCHID",
          "ACCTID",
          "ACCTTYPE",
          "FITID",
          "DTPOSTED",
          "TRNAMT",
          "NAME",
          "MEMO",
          "TRNTYPE",
          "CODE",
          "SEVERITY",
          "MESSAGE",
          "DTSERVER",
          "LANGUAGE",
          "ORG",
          "FID",
          "TRNUID",
          "DTSTART",
          "DTEND",
          "BALAMT",
          "DTASOF",
          "CORRECTFITID",
          "CORRECTACTION",
          "CHECKNUM",
          "REFNUM",
          "SRVRTID");
  private static final Pattern TOKEN = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9_]*)(/?)>|([^<]+)");

  public record Row(
      int number,
      String fitId,
      String description,
      LocalDate date,
      BigDecimal amount,
      String error) {
    public FinancialEntry.Type type() {
      return amount.signum() > 0 ? FinancialEntry.Type.INCOME : FinancialEntry.Type.EXPENSE;
    }

    public String fingerprint() {
      return hash(date + "|" + amount.toPlainString() + "|" + description);
    }
  }

  public record Statement(String sourceHash, String bank, String accountHint, List<Row> rows) {}

  private static final class Node {
    final String tag;
    String text = "";
    final List<Node> children = new ArrayList<>();

    Node(String tag) {
      this.tag = tag;
    }

    List<Node> all(String tag) {
      var out = new ArrayList<Node>();
      if (this.tag.equals(tag)) out.add(this);
      for (var c : children) out.addAll(c.all(tag));
      return out;
    }

    String value(String tag, boolean required) {
      var found = children.stream().filter(c -> c.tag.equals(tag)).toList();
      if (found.size() > 1 || (required && found.isEmpty()))
        throw invalid("Campo OFX ausente ou repetido: " + tag + ".");
      if (found.isEmpty()) return "";
      var n = found.get(0);
      if (!n.children.isEmpty()) throw invalid("Estrutura inválida em " + tag + ".");
      var value = decode(n.text.strip());
      if (required && value.isBlank()) throw invalid("Campo OFX vazio: " + tag + ".");
      return value;
    }
  }

  public static Statement parse(String input) {
    if (input == null || input.getBytes(StandardCharsets.UTF_8).length > 1048576)
      throw invalid("O OFX deve ter até 1 MB.");
    if (Pattern.compile("<!", Pattern.CASE_INSENSITIVE).matcher(input).find())
      throw invalid("Declarações, comentários e entidades XML não são aceitos no OFX.");
    String text = input.replace("\ufeff", "").replaceAll("(?is)<\\?(?:xml|ofx)\\s[^?]*\\?>", "");
    int rootStart = text.toUpperCase(Locale.ROOT).indexOf("<OFX>");
    if (rootStart < 0) throw invalid("Arquivo sem a estrutura OFX bancária.");
    String prefix = text.substring(0, rootStart).strip();
    if (!prefix.isEmpty() && !prefix.matches("(?is)(?:[A-Z]+:[^<>\\r\\n]+\\s*)+"))
      throw invalid("Cabeçalho OFX inválido.");
    text = text.substring(rootStart);
    var root = new Node("DOCUMENT");
    var stack = new ArrayDeque<Node>();
    stack.push(root);
    var matcher = TOKEN.matcher(text);
    int end = 0, tokens = 0;
    while (matcher.find()) {
      if (matcher.start() != end || ++tokens > 20000)
        throw invalid("Estrutura OFX inválida ou muito complexa.");
      end = matcher.end();
      if (matcher.group(4) != null) {
        if (!matcher.group(4).isBlank()) {
          if (stack.size() < 2 || !stack.peek().children.isEmpty())
            throw invalid("Texto fora de um campo OFX.");
          stack.peek().text += matcher.group(4);
        }
        continue;
      }
      String tag = matcher.group(2).toUpperCase(Locale.ROOT);
      boolean close = !matcher.group(1).isEmpty();
      if (stack.size() > 1
          && !stack.peek().tag.equals(tag)
          && (SCALARS.contains(stack.peek().tag) || !stack.peek().text.isBlank())) stack.pop();
      if (close) {
        if (stack.size() <= 1 || !stack.peek().tag.equals(tag))
          throw invalid("Fechamento OFX inválido em " + tag + ".");
        stack.pop();
      } else {
        if (stack.size() > 32 || !stack.peek().text.isBlank())
          throw invalid("Estrutura OFX inválida.");
        var node = new Node(tag);
        stack.peek().children.add(node);
        if (matcher.group(3).isEmpty()) stack.push(node);
      }
    }
    if (end != text.length()
        || stack.size() != 1
        || root.children.size() != 1
        || !root.children.get(0).tag.equals("OFX"))
      throw invalid("OFX incompleto ou com mais de um documento.");
    var statements = root.all("STMTRS");
    if (statements.size() != 1
        || !root.all("CCSTMTRS").isEmpty()
        || !root.all("INVSTMTRS").isEmpty())
      throw invalid(
          "Use um extrato bancário de uma única conta; cartões e investimentos não são aceitos"
              + " nesta etapa.");
    for (var status : root.all("STATUS"))
      if (!status.value("CODE", true).equals("0"))
        throw invalid("O arquivo contém uma resposta de erro do banco.");
    var statement = statements.get(0);
    if (!statement.value("CURDEF", true).equals("BRL"))
      throw invalid("Somente extratos em reais (BRL) são aceitos.");
    var origins = statement.all("BANKACCTFROM");
    if (origins.size() != 1 || statement.all("BANKTRANLIST").size() != 1)
      throw invalid("Identificação da conta ou lista de movimentos inválida.");
    var origin = origins.get(0);
    var bank = limited(origin.value("BANKID", true), 40, "Banco");
    var branch = limited(origin.value("BRANCHID", false), 40, "Agência");
    var account = limited(origin.value("ACCTID", true), 120, "Conta bancária");
    var accountType = limited(origin.value("ACCTTYPE", true), 20, "Tipo de conta");
    var nodes = statement.all("STMTTRN");
    if (nodes.size() > 500
        || nodes.size() != statement.all("BANKTRANLIST").get(0).all("STMTTRN").size())
      throw invalid("Use até 500 movimentos na lista do extrato.");
    if (nodes.isEmpty()) throw invalid("O extrato não contém movimentos.");
    var rows = new ArrayList<Row>();
    for (var n : nodes) {
      String fit = "", description = "";
      LocalDate date = null;
      BigDecimal amount = null;
      try {
        fit = limited(n.value("FITID", true), 255, "Identificador bancário");
        if (!n.value("CORRECTFITID", false).isEmpty() || !n.value("CORRECTACTION", false).isEmpty())
          throw invalid("Correções bancárias exigem revisão manual fora desta importação.");
        String rawDate = n.value("DTPOSTED", true);
        if (!rawDate.matches(
            "[0-9]{8}(?:[0-9]{6}(?:\\.[0-9]{1,3})?(?:\\[[+-]?[0-9]{1,2}(?:\\.[0-9]+)?(?::[^\\[\\]<>]{1,20})?\\])?)?"))
          throw invalid("Data OFX inválida.");
        date = LocalDate.parse(rawDate.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        if (rawDate.length() >= 14)
          LocalTime.of(
              Integer.parseInt(rawDate.substring(8, 10)),
              Integer.parseInt(rawDate.substring(10, 12)),
              Integer.parseInt(rawDate.substring(12, 14)));
        if (date.getYear() < 1900 || date.isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo"))))
          throw invalid("Movimento com data futura ou anterior a 1900.");
        String rawAmount = n.value("TRNAMT", true);
        if (!rawAmount.matches("[+-]?[0-9]{1,12}(?:\\.[0-9]{1,2})?"))
          throw invalid("Valor OFX inválido; use ponto decimal e até duas casas.");
        amount = new BigDecimal(rawAmount).setScale(2);
        if (amount.signum() == 0) throw invalid("Movimentos com valor zero não são importados.");
        String name = n.value("NAME", false), memo = n.value("MEMO", false);
        description =
            name.isBlank()
                ? memo
                : memo.isBlank() || memo.equals(name) ? name : name + " · " + memo;
        if (description.isBlank()) description = "Movimento bancário " + fit;
        description = limited(description, 255, "Descrição");
        rows.add(new Row(rows.size() + 1, fit, description, date, amount, null));
      } catch (ResponseStatusException | DateTimeException ex) {
        rows.add(
            new Row(
                rows.size() + 1,
                fit,
                description,
                date,
                amount,
                ex instanceof ResponseStatusException r ? r.getReason() : "Data OFX inválida."));
      }
    }
    return new Statement(
        hash(
            bank.length()
                + ":"
                + bank
                + branch.length()
                + ":"
                + branch
                + account.length()
                + ":"
                + account
                + accountType.length()
                + ":"
                + accountType),
        bank,
        "••••" + account.substring(Math.max(0, account.length() - 4)),
        rows);
  }

  private static String limited(String value, int max, String field) {
    if (value.length() > max || value.codePoints().anyMatch(c -> Character.isISOControl(c)))
      throw invalid(field + " excede o limite ou contém caracteres de controle.");
    return value;
  }

  private static String decode(String value) {
    var m = Pattern.compile("&(#x[0-9A-Fa-f]+|#[0-9]+|amp|lt|gt|quot|apos);").matcher(value);
    var out = new StringBuilder();
    int end = 0;
    while (m.find()) {
      String prefix = value.substring(end, m.start());
      if (prefix.contains("&")) throw invalid("Entidade OFX não suportada.");
      out.append(prefix);
      String code = m.group(1);
      String replacement =
          switch (code) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            default -> "";
          };
      if (code.startsWith("#")) {
        try {
          int cp =
              Integer.parseInt(
                  code.substring(code.startsWith("#x") ? 2 : 1), code.startsWith("#x") ? 16 : 10);
          if (!Character.isValidCodePoint(cp) || cp == 0 || cp >= 0xD800 && cp <= 0xDFFF)
            throw new NumberFormatException();
          out.appendCodePoint(cp);
        } catch (NumberFormatException ex) {
          throw invalid("Entidade numérica OFX inválida.");
        }
      } else out.append(replacement);
      end = m.end();
    }
    if (value.substring(end).contains("&")) throw invalid("Escape & inválido no OFX; use &amp;.");
    return out.append(value.substring(end)).toString();
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
