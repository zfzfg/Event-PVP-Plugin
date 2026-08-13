package de.zfzfg.core.world.mv;

/**
 * Abgelehnte Eingabe aus dem Webinterface -- ungueltiger Weltname, unbekanntes Environment,
 * gesperrte Hauptwelt.
 *
 * <p>Traegt wie {@link MvResult} einen Bundle-Key statt eines fertigen Satzes, damit das Panel
 * den Grund uebersetzt anzeigen kann. Die Exception-Message selbst bleibt der Key -- sie ist
 * nur fuer Logs gedacht und erreicht nie einen Spieler.</p>
 */
public class MvInputException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final String detail;

    public MvInputException(String messageKey) {
        this(messageKey, "");
    }

    public MvInputException(String messageKey, String detail) {
        super(messageKey + (detail == null || detail.isEmpty() ? "" : ": " + detail));  // i18n-ignore: Exception-Text nur fuers Server-Log; das Panel liest messageKey/detail
        this.messageKey = messageKey;
        this.detail = detail == null ? "" : detail;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getDetail() {
        return detail;
    }
}
