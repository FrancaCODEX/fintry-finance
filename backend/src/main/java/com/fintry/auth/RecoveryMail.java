package com.fintry.auth;

import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class RecoveryMail {
  public record Message(String email, String link) {}

  private final JavaMailSender sender;
  private final Executor executor;
  private final String from;
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RecoveryMail.class);

  public RecoveryMail(
      JavaMailSender sender,
      @org.springframework.beans.factory.annotation.Qualifier("recoveryExecutor") Executor executor,
      @Value("${fintry.mail.from:noreply@fintry.local}") String from) {
    this.sender = sender;
    this.executor = executor;
    this.from = from;
  }

  @Bean(name = "recoveryExecutor")
  static Executor executor() {
    var pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(1);
    pool.setMaxPoolSize(2);
    pool.setQueueCapacity(50);
    pool.setThreadNamePrefix("fintry-mail-");
    pool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
    return pool;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void send(Message request) {
    executor.execute(
        () -> {
          try {
            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(request.email());
            message.setSubject("Fintry · Redefinir sua senha");
            message.setText(
                "Recebemos uma solicitação para redefinir sua senha no Fintry.\n\n"
                    + "Abra o link abaixo em até 15 minutos. Ele pode ser usado uma única vez:\n"
                    + request.link()
                    + "\n\n"
                    + "Se você não solicitou, ignore este e-mail. Sua senha só muda depois da"
                    + " confirmação pelo link.");
            sender.send(message);
          } catch (Exception ex) {
            log.warn(
                "Não foi possível entregar um e-mail de recuperação. Confira a configuração SMTP.");
          }
        });
  }
}
