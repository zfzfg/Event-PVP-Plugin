package de.zfzfg.core.world.mv;

/**
 * Ergebnis einer Multiverse-Operation.
 *
 * <p>Ein Fehlschlag traegt <em>keinen</em> fertigen Satz, sondern einen
 * {@code messageKey} aus den Web-Bundles ({@code web/lang/*.json}, Prefix {@code mv.error.}),
 * den das Panel in der Sprache des Admins aufloest. Nur {@code detail} ist untranslatierbarer
 * Technik-Text -- der Fehlergrund von Multiverse selbst oder ein Exception-Text -- und wird im
 * Panel hinter dem uebersetzten Satz in Klammern angehaengt.</p>
 *
 * <p>Bei Erfolg formuliert das Panel seine Meldung ohnehin selbst
 * ({@code toast.mvCreated} usw.), deshalb bleibt der Key dort leer.</p>
 */
public class MvResult {

    /** Fallback, wenn ein Fehlschlag keinen spezifischeren Key mitbringt. */
    public static final String GENERIC_ERROR = "mv.error.generic";

    private final boolean success;
    private final String messageKey;
    private final String detail;

    private MvResult(boolean success, String messageKey, String detail) {
        this.success = success;
        this.messageKey = messageKey == null ? "" : messageKey;
        this.detail = detail == null ? "" : detail;
    }

    public static MvResult ok() {
        return new MvResult(true, "", "");
    }

    /** Fehlschlag mit uebersetzbarem Grund. */
    public static MvResult fail(String messageKey) {
        return new MvResult(false, messageKey, "");
    }

    /** Fehlschlag mit uebersetzbarem Grund plus technischem Zusatz (Multiverse-Text, Exception). */
    public static MvResult fail(String messageKey, String detail) {
        return new MvResult(false, messageKey, detail);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getDetail() {
        return detail;
    }
}
