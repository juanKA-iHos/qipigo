package pe.ihos.qipigo;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Parses Apple manufacturer data (company ID 0x004c is provided separately by Android). */
public final class BeaconFrame {
    public static final UUID PILOT_UUID = UUID.fromString("7777772E-6B6B-6D63-6E2E-636F6D000001");
    public final UUID uuid;
    public final int major, minor, measuredPower;
    private BeaconFrame(UUID uuid, int major, int minor, int measuredPower) {
        this.uuid = uuid; this.major = major; this.minor = minor; this.measuredPower = measuredPower;
    }
    public static BeaconFrame parse(byte[] data) {
        if (data == null || data.length < 23 || data[0] != 0x02 || data[1] != 0x15) return null;
        ByteBuffer b = ByteBuffer.wrap(data);
        b.position(2);
        return new BeaconFrame(new UUID(b.getLong(), b.getLong()), b.getShort() & 0xffff,
            b.getShort() & 0xffff, b.get());
    }
    public boolean isPilot() { return uuid.equals(PILOT_UUID) && major == 1 && (minor == 1 || minor == 2); }
}
