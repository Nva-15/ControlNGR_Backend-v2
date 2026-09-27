-- Control NGR - Restablece la contraseña del usuario admin a la inicial ($.4dmin2026)
-- y obliga a cambiarla en el siguiente ingreso. Uso: ver GUIA_INSTALACION.md (problemas frecuentes).
UPDATE usuarios
   SET password = '$2a$10$zT.Yl8FggGB9Fi0RdeiwZOn7wMASLFM2xLuteEjIl05f7Pp3Fp6.K',
       debe_cambiar_password = 1,
       activo = 1
 WHERE username = 'admin';
