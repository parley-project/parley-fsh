package us.bringardner.fsh;

/**
 * Marks an InputStream that is fed by a person typing (for example the GUI console).
 * It never reaches end of file and is shared with the shell, so a reader that copies it to
 * another consumer must poll {@link java.io.InputStream#available()} rather than block in read(),
 * otherwise it would take the next line meant for the shell.
 */
public interface InteractiveInput {

}
