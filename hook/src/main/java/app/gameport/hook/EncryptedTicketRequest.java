package app.gameport.hook;

/**
 * A request for an encrypted app ticket, as the game's Steam stand-in writes it in the game's {@code gameport} folder ({@code encrypted_ticket.request}): the first line is the id of the
 * request, the second what the game asked to have put in the ticket ({@code RequestEncryptedAppTicket(data, size)}), in hexadecimal, and empty when the game gave nothing.
 *
 * The answer goes the other way in two files, as for a session ticket: {@code encrypted_ticket.bin} (the ticket, left out when there is none) and then {@code encrypted_ticket.ready}
 * holding the same id. These files are not the ones of the session ticket: a ticket of the other kind must never be taken for it.
 */
final class EncryptedTicketRequest {
    /** Steam takes at most 1024 bytes of data for the ticket; a request that gives more is not one. */
    static final int MAX_USERDATA = 1024;

    final String id;
    final byte[] userdata;

    private EncryptedTicketRequest(String id, byte[] userdata) {
        this.id = id;
        this.userdata = userdata;
    }

    /** The request made of its two lines, or null when it has no id or its data is not hexadecimal or too long. */
    static EncryptedTicketRequest of(String idLine, String dataLine) {
        if (idLine == null || idLine.trim().isEmpty()) return null;
        byte[] data = fromHex(dataLine == null ? "" : dataLine.trim());
        if (data == null) return null;
        return new EncryptedTicketRequest(idLine.trim(), data);
    }

    /** The bytes a string of hexadecimal digits stands for, or null when it is not one (odd length, another character) or holds more than {@link #MAX_USERDATA} bytes. */
    static byte[] fromHex(String hex) {
        if (hex.length() % 2 != 0 || hex.length() / 2 > MAX_USERDATA) return null;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(hex.charAt(2 * i), 16);
            int low = Character.digit(hex.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) return null;
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }
}
