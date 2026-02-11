package com.example.ControlNGR.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.Async;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.mail.from:proyecto.24web@gmail.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:Sistema Control NGR}")
    private String fromName;

    /** Notifica al empleado sobre el estado de su solicitud. */
    @Async
    public void enviarNotificacionSolicitud(String destinatarioEmail, String empleadoNombre,
                                            String tipoSolicitud, String estado,
                                            String comentarios, String fechaInicio,
                                            String fechaFin, String gestionadoPor) {
        if (!emailEnabled || destinatarioEmail == null || destinatarioEmail.isEmpty()) {
            logger.warn("Email deshabilitado o destinatario vacío");
            return;
        }

        try {
            String tipoFormateado = formatearTipoSolicitud(tipoSolicitud);
            String estadoFormateado = formatearEstado(estado);

            String asunto;
            if ("aprobado".equalsIgnoreCase(estado)) {
                asunto = "Solicitud de " + tipoFormateado + " APROBADA - ControlNGR";
            } else {
                asunto = "Solicitud RECHAZADA - ControlNGR";
            }
            String contenido = construirPlantillaNotificacionEstado(
                    empleadoNombre, tipoFormateado, estadoFormateado,
                    fechaInicio, fechaFin, comentarios, gestionadoPor
            );

            enviarEmail(destinatarioEmail, asunto, contenido);
            logger.info("Notificación de solicitud enviada a: {}", destinatarioEmail);

        } catch (Exception e) {
            logger.error("Error enviando notificación de solicitud a {}: {}", destinatarioEmail, e.getMessage());
        }
    }

    /** Notifica a supervisores cuando se crea una nueva solicitud. */
    @Async
    public void enviarNotificacionNuevaSolicitud(String destinatarioEmail, String supervisorNombre,
                                                  String empleadoNombre, String tipoSolicitud,
                                                  String fechaInicio, String fechaFin) {
        if (!emailEnabled || destinatarioEmail == null || destinatarioEmail.isEmpty()) {
            logger.warn("Email deshabilitado o destinatario vacío");
            return;
        }

        try {
            String tipoFormateado = formatearTipoSolicitud(tipoSolicitud);

            String asunto = "NUEVA SOLICITUD PENDIENTE - Sistema ControlNGR";
            String contenido = construirPlantillaNuevaSolicitud(
                    supervisorNombre, empleadoNombre, tipoFormateado, fechaInicio, fechaFin
            );

            enviarEmail(destinatarioEmail, asunto, contenido);
            logger.info("Notificación de nueva solicitud enviada a supervisor: {}", destinatarioEmail);

        } catch (Exception e) {
            logger.error("Error enviando notificación a supervisor {}: {}", destinatarioEmail, e.getMessage());
        }
    }

    /** Notifica a admin cuando un supervisor crea una solicitud. */
    @Async
    public void enviarNotificacionSolicitudSupervisor(String destinatarioEmail, String adminNombre,
                                                       String supervisorNombre, String tipoSolicitud,
                                                       String fechaInicio, String fechaFin) {
        if (!emailEnabled || destinatarioEmail == null || destinatarioEmail.isEmpty()) {
            logger.warn("Email deshabilitado o destinatario vacío");
            return;
        }

        try {
            String tipoFormateado = formatearTipoSolicitud(tipoSolicitud);

            String asunto = "SOLICITUD DE SUPERVISOR PENDIENTE - Sistema ControlNGR";
            String contenido = construirPlantillaSolicitudSupervisor(
                    adminNombre, supervisorNombre, tipoFormateado, fechaInicio, fechaFin
            );

            enviarEmail(destinatarioEmail, asunto, contenido);
            logger.info("Notificación de solicitud de supervisor enviada a admin: {}", destinatarioEmail);

        } catch (Exception e) {
            logger.error("Error enviando notificación a admin {}: {}", destinatarioEmail, e.getMessage());
        }
    }

    /** Notifica al empleado sobre cambio de contraseña. */
    @Async
    public void enviarNotificacionCambioPassword(String destinatarioEmail, String empleadoNombre) {
        if (!emailEnabled || destinatarioEmail == null || destinatarioEmail.isEmpty()) {
            logger.warn("Email deshabilitado o destinatario vacío");
            return;
        }

        try {
            String asunto = "Cambio de Contraseña - Sistema ControlNGR";
            String contenido = construirPlantillaCambioPassword(empleadoNombre);

            enviarEmail(destinatarioEmail, asunto, contenido);
            logger.info("Notificación de cambio de password enviada a: {}", destinatarioEmail);

        } catch (Exception e) {
            logger.error("Error enviando notificación de cambio de password a {}: {}", destinatarioEmail, e.getMessage());
        }
    }

    /** Notifica al empleado sobre actualización de perfil. */
    @Async
    public void enviarNotificacionActualizacionPerfil(String destinatarioEmail, String empleadoNombre) {
        if (!emailEnabled || destinatarioEmail == null || destinatarioEmail.isEmpty()) {
            logger.warn("Email deshabilitado o destinatario vacío");
            return;
        }

        try {
            String asunto = "Actualización de Perfil - Sistema ControlNGR";
            String contenido = construirPlantillaActualizacionPerfil(empleadoNombre);

            enviarEmail(destinatarioEmail, asunto, contenido);
            logger.info("Notificación de actualización de perfil enviada a: {}", destinatarioEmail);

        } catch (Exception e) {
            logger.error("Error enviando notificación de actualización de perfil a {}: {}", destinatarioEmail, e.getMessage());
        }
    }

    private void enviarEmail(String destinatario, String asunto, String contenidoHtml)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(destinatario);
        helper.setSubject(asunto);
        helper.setText(contenidoHtml, true);

        mailSender.send(mensaje);
    }

    private String construirPlantillaNotificacionEstado(String empleadoNombre, String tipo,
                                                         String estado, String fechaInicio,
                                                         String fechaFin, String comentarios,
                                                         String gestionadoPor) {
        String colorEstado = estado.contains("Aprobado") ? "#28a745" : "#dc3545";
        String accion = estado.contains("Aprobado") ? "aprobada" : "rechazada";

        String seccionComentarios = "";
        if (comentarios != null && !comentarios.trim().isEmpty()) {
            if (estado.contains("Rechazado")) {
                seccionComentarios = "<div style='background:#f8d7da;border:1px solid #f5c6cb;padding:12px;border-radius:5px;margin-top:10px;'>"
                    + "<p style='margin:0;color:#721c24;'><strong>Motivo del rechazo:</strong> " + comentarios + "</p></div>";
            } else {
                seccionComentarios = "<p><strong>Comentarios:</strong> " + comentarios + "</p>";
            }
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; }
                    .content { padding: 30px; }
                    .estado { display: inline-block; padding: 10px 20px; border-radius: 5px; color: white; font-weight: bold; background-color: %s; }
                    .info-box { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #667eea; }
                    .gestionado { background: #e7f3ff; padding: 12px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #007bff; }
                    .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Sistema ControlNGR</h1>
                        <p>Notificación de Solicitud</p>
                    </div>
                    <div class="content">
                        <p>Hola <strong>%s</strong>,</p>
                        <p>Tu solicitud de <strong>%s</strong> ha sido <strong>%s</strong>. A continuación el detalle:</p>

                        <div class="info-box">
                            <p><strong>Tipo:</strong> %s</p>
                            <p><strong>Estado:</strong> <span class="estado">%s</span></p>
                            <p><strong>Período:</strong> %s al %s</p>
                            %s
                        </div>

                        <div class="gestionado">
                            <p><strong>Gestionado por:</strong> %s</p>
                            <p><strong>Fecha de gestión:</strong> %s</p>
                        </div>

                        <p>Si tienes alguna pregunta, contacta a tu supervisor.</p>
                    </div>
                    <div class="footer">
                        <p>Sistema ControlNGR - Notificación automática</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                colorEstado,
                empleadoNombre,
                tipo,
                accion,
                tipo,
                estado,
                fechaInicio,
                fechaFin,
                seccionComentarios,
                gestionadoPor != null ? gestionadoPor : "Sistema",
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a las " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        );
    }

    private String construirPlantillaNuevaSolicitud(String supervisorNombre, String empleadoNombre,
                                                     String tipo, String fechaInicio, String fechaFin) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); color: white; padding: 30px; text-align: center; }
                    .content { padding: 30px; }
                    .alert { background: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 5px; margin: 15px 0; }
                    .info-box { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #f5576c; }
                    .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                    .btn { display: inline-block; padding: 12px 24px; background: #667eea; color: white; text-decoration: none; border-radius: 5px; margin-top: 15px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Nueva Solicitud Pendiente</h1>
                        <p>Requiere tu revisión</p>
                    </div>
                    <div class="content">
                        <p>Hola <strong>%s</strong>,</p>

                        <div class="alert">
                            <strong>Acción requerida:</strong> Tienes una nueva solicitud pendiente de revisión.
                        </div>

                        <div class="info-box">
                            <p><strong>Empleado:</strong> %s</p>
                            <p><strong>Tipo de Solicitud:</strong> %s</p>
                            <p><strong>Período Solicitado:</strong> %s al %s</p>
                            <p><strong>Fecha de Solicitud:</strong> %s</p>
                        </div>

                        <p>Por favor, ingresa al sistema para revisar y gestionar esta solicitud.</p>
                    </div>
                    <div class="footer">
                        <p>Sistema ControlNGR - Notificación automática</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                supervisorNombre,
                empleadoNombre,
                tipo,
                fechaInicio,
                fechaFin,
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        );
    }

    private String construirPlantillaSolicitudSupervisor(String adminNombre, String supervisorNombre,
                                                          String tipo, String fechaInicio, String fechaFin) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #fa709a 0%%, #fee140 100%%); color: white; padding: 30px; text-align: center; }
                    .content { padding: 30px; }
                    .alert { background: #f8d7da; border: 1px solid #f5c6cb; padding: 15px; border-radius: 5px; margin: 15px 0; color: #721c24; }
                    .info-box { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #fa709a; }
                    .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                    .important { background: #e2e3e5; padding: 10px; border-radius: 5px; margin-top: 15px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Solicitud de Supervisor</h1>
                        <p>Requiere aprobación de Administrador</p>
                    </div>
                    <div class="content">
                        <p>Hola <strong>%s</strong>,</p>

                        <div class="alert">
                            <strong>Importante:</strong> Un supervisor ha creado una solicitud que solo puede ser aprobada por un administrador.
                        </div>

                        <div class="info-box">
                            <p><strong>Supervisor:</strong> %s</p>
                            <p><strong>Tipo de Solicitud:</strong> %s</p>
                            <p><strong>Período Solicitado:</strong> %s al %s</p>
                            <p><strong>Fecha de Solicitud:</strong> %s</p>
                        </div>

                        <div class="important">
                            <p><strong>Nota:</strong> Los supervisores no pueden auto-aprobar sus solicitudes. Solo los administradores tienen permisos para esta acción.</p>
                        </div>

                        <p>Por favor, ingresa al sistema para revisar esta solicitud.</p>
                    </div>
                    <div class="footer">
                        <p>Sistema ControlNGR - Notificación automática</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                adminNombre,
                supervisorNombre,
                tipo,
                fechaInicio,
                fechaFin,
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        );
    }

    private String construirPlantillaCambioPassword(String empleadoNombre) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #11998e 0%%, #38ef7d 100%%); color: white; padding: 30px; text-align: center; }
                    .content { padding: 30px; }
                    .success { background: #d4edda; border: 1px solid #c3e6cb; padding: 15px; border-radius: 5px; margin: 15px 0; color: #155724; }
                    .warning { background: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 5px; margin: 15px 0; }
                    .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Cambio de Contraseña</h1>
                        <p>Confirmación de seguridad</p>
                    </div>
                    <div class="content">
                        <p>Hola <strong>%s</strong>,</p>

                        <div class="success">
                            <strong>Contraseña actualizada exitosamente</strong>
                        </div>

                        <p>Tu contraseña ha sido cambiada el <strong>%s</strong> a las <strong>%s</strong>.</p>

                        <div class="warning">
                            <strong>Si no realizaste este cambio:</strong>
                            <ul>
                                <li>Contacta inmediatamente al administrador</li>
                                <li>Cambia tu contraseña nuevamente</li>
                                <li>Reporta cualquier actividad sospechosa</li>
                            </ul>
                        </div>
                    </div>
                    <div class="footer">
                        <p>Sistema ControlNGR - Notificación de seguridad</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                empleadoNombre,
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        );
    }

    private String construirPlantillaActualizacionPerfil(String empleadoNombre) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; }
                    .content { padding: 30px; }
                    .success { background: #d4edda; border: 1px solid #c3e6cb; padding: 15px; border-radius: 5px; margin: 15px 0; color: #155724; }
                    .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Perfil Actualizado</h1>
                    </div>
                    <div class="content">
                        <p>Hola <strong>%s</strong>,</p>

                        <div class="success">
                            <strong>Tu información de perfil ha sido actualizada correctamente.</strong>
                        </div>

                        <p>Fecha de actualización: <strong>%s</strong> a las <strong>%s</strong></p>

                        <p>Por favor verifica que toda tu información esté correcta ingresando al sistema.</p>
                    </div>
                    <div class="footer">
                        <p>Sistema ControlNGR - Notificación automática</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                empleadoNombre,
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        );
    }

    private String formatearTipoSolicitud(String tipo) {
        if (tipo == null) return "Solicitud";
        return switch (tipo.toLowerCase()) {
            case "vacaciones" -> "Vacaciones";
            case "permiso" -> "Permiso Personal";
            case "descanso" -> "Descanso Médico";
            case "compensacion" -> "Compensación de Horas";
            case "licencia" -> "Licencia Especial";
            default -> tipo;
        };
    }

    private String formatearEstado(String estado) {
        if (estado == null) return "Pendiente";
        return switch (estado.toLowerCase()) {
            case "aprobado" -> "Aprobado";
            case "rechazado" -> "Rechazado";
            case "pendiente" -> "Pendiente";
            case "en_revision" -> "En Revisión";
            default -> estado;
        };
    }
}
