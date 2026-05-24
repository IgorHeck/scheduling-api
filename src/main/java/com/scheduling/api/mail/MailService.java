package com.scheduling.api.mail;

import com.scheduling.api.appointment.model.Appointment;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serviço de envio de e-mails HTML.
 * Todos os métodos são @Async — o chamador não bloqueia e falhas de entrega
 * são apenas logadas, sem propagação de exceção.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@scheduling.app}")
    private String fromAddress;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    // ── Agendamentos ─────────────────────────────────────────────────────────

    @Async
    public void sendAppointmentPending(Appointment a) {
        String subject = "⏳ Solicitação de agendamento recebida — " + a.getCompany().getName();
        String body = """
            <p>Olá, <strong>%s</strong>!</p>
            <p>Recebemos sua solicitação de agendamento e ela está <strong>aguardando confirmação</strong>.</p>
            %s
            <p style="color:#6b7280;font-size:13px">
              Você receberá outro e-mail assim que o agendamento for confirmado.
            </p>
            """.formatted(a.getClient().getName(), appointmentBlock(a));
        send(a.getClient().getEmail(), subject, wrapHtml(subject, body));
    }

    @Async
    public void sendAppointmentConfirmed(Appointment a) {
        String subject = "✅ Agendamento confirmado — " + a.getCompany().getName();
        String body = """
            <p>Olá, <strong>%s</strong>!</p>
            <p>Seu agendamento foi <strong style="color:#16a34a">confirmado</strong>. Te esperamos!</p>
            %s
            <p style="color:#6b7280;font-size:13px">
              Para cancelar ou remarcar, acesse o aplicativo ou entre em contato com a empresa.
            </p>
            """.formatted(a.getClient().getName(), appointmentBlock(a));
        send(a.getClient().getEmail(), subject, wrapHtml(subject, body));
    }

    @Async
    public void sendAppointmentCancelled(Appointment a, String reason) {
        String subject = "❌ Agendamento cancelado — " + a.getCompany().getName();
        String reasonHtml = (reason != null && !reason.isBlank())
                ? "<p><strong>Motivo:</strong> " + reason + "</p>"
                : "";
        String body = """
            <p>Olá, <strong>%s</strong>!</p>
            <p>Seu agendamento foi <strong style="color:#dc2626">cancelado</strong>.</p>
            %s
            %s
            <p style="color:#6b7280;font-size:13px">
              Sentimos muito pela inconveniência. Você pode solicitar um novo agendamento quando desejar.
            </p>
            """.formatted(a.getClient().getName(), appointmentBlock(a), reasonHtml);
        send(a.getClient().getEmail(), subject, wrapHtml(subject, body));
    }

    @Async
    public void sendAppointmentRescheduled(Appointment a) {
        String subject = "🔄 Agendamento remarcado — " + a.getCompany().getName();
        String body = """
            <p>Olá, <strong>%s</strong>!</p>
            <p>Seu agendamento foi <strong>remarcado</strong> para o novo horário abaixo
               e está <strong>aguardando confirmação</strong>.</p>
            %s
            <p style="color:#6b7280;font-size:13px">
              Você receberá outro e-mail quando o novo horário for confirmado.
            </p>
            """.formatted(a.getClient().getName(), appointmentBlock(a));
        send(a.getClient().getEmail(), subject, wrapHtml(subject, body));
    }

    // ── Recuperação de senha ─────────────────────────────────────────────────

    @Async
    public void sendPasswordResetEmail(String toEmail, String userName, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String subject   = "🔑 Redefinição de senha";
        String body = """
            <p>Olá, <strong>%s</strong>!</p>
            <p>Recebemos uma solicitação para redefinir a senha da sua conta.</p>
            <p>Clique no botão abaixo para criar uma nova senha. O link é válido por <strong>1 hora</strong>.</p>
            <div style="text-align:center;margin:28px 0">
              <a href="%s"
                 style="background:#2563eb;color:#fff;text-decoration:none;
                        padding:12px 28px;border-radius:8px;font-weight:600;font-size:15px">
                Redefinir Senha
              </a>
            </div>
            <p style="color:#6b7280;font-size:13px">
              Se você não solicitou essa redefinição, ignore este e-mail — sua senha continua a mesma.
            </p>
            <p style="color:#9ca3af;font-size:12px;word-break:break-all">
              Ou copie e cole este link no navegador:<br>%s
            </p>
            """.formatted(userName, resetLink, resetLink);
        send(toEmail, subject, wrapHtml(subject, body));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String appointmentBlock(Appointment a) {
        String startFmt = a.getStartAt().format(DT_FMT);
        String endTime  = a.getEndAt().format(DateTimeFormatter.ofPattern("HH:mm"));
        String notes    = (a.getNotes() != null && !a.getNotes().isBlank())
                ? "<p><strong>💬 Observação:</strong> " + a.getNotes() + "</p>"
                : "";
        return """
            <div style="background:#f0f9ff;border:1px solid #bae6fd;border-radius:8px;
                        padding:16px 20px;margin:16px 0;line-height:1.8">
              <p style="margin:0">📅 <strong>Data:</strong> %s – %s</p>
              <p style="margin:0">👤 <strong>Profissional:</strong> %s</p>
              <p style="margin:0">🏢 <strong>Local:</strong> %s</p>
              %s
            </div>
            """.formatted(startFmt, endTime, a.getProfessional().getName(),
                          a.getCompany().getName(), notes);
    }

    private String wrapHtml(String title, String content) {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,Helvetica,sans-serif;background:#f9fafb;margin:0;padding:0">
              <table width="100%%" cellpadding="0" cellspacing="0">
                <tr><td align="center" style="padding:32px 16px">
                  <div style="background:#fff;border-radius:12px;max-width:560px;
                              padding:32px 36px;border:1px solid #e5e7eb;color:#111827">
                    <p style="font-size:22px;font-weight:700;color:#1d4ed8;margin:0 0 20px">
                      Scheduling
                    </p>
                    %s
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0">
                    <p style="color:#9ca3af;font-size:12px;margin:0">
                      Este é um e-mail automático, por favor não responda.
                    </p>
                  </div>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(content);
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("[Mail] Enviado para {} — {}", to, subject);
        } catch (MessagingException e) {
            log.error("[Mail] Falha ao enviar para {} — {}: {}", to, subject, e.getMessage());
        }
    }
}
