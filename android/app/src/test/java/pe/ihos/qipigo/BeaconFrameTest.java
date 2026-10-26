package pe.ihos.qipigo;
import org.junit.Test;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;
public class BeaconFrameTest {
    private byte[] packet(int major, int minor) {
        return ByteBuffer.allocate(23).put((byte)2).put((byte)0x15)
            .putLong(BeaconFrame.PILOT_UUID.getMostSignificantBits())
            .putLong(BeaconFrame.PILOT_UUID.getLeastSignificantBits())
            .putShort((short)major).putShort((short)minor).put((byte)-59).array();
    }
    @Test public void decodesBothPilotDevices() {
        for (int minor : new int[]{1,2}) {
            BeaconFrame f = BeaconFrame.parse(packet(1,minor));
            assertNotNull(f); assertTrue(f.isPilot()); assertEquals(minor,f.minor); assertEquals(-59,f.measuredPower);
        }
    }
    @Test public void rejectsMalformedAndForeignFrames() {
        assertNull(BeaconFrame.parse(null)); assertNull(BeaconFrame.parse(new byte[22]));
        byte[] wrong = packet(1,1); wrong[0] = 3; assertNull(BeaconFrame.parse(wrong));
        assertFalse(BeaconFrame.parse(packet(1,3)).isPilot());
        assertFalse(BeaconFrame.parse(packet(2,1)).isPilot());
        byte[] foreign = packet(1,1); foreign[2] ^= 1; assertFalse(BeaconFrame.parse(foreign).isPilot());
    }
    @Test public void decodesUnsignedIdentifiers() {
        BeaconFrame f = BeaconFrame.parse(packet(65535,32768));
        assertEquals(65535,f.major); assertEquals(32768,f.minor);
    }
}
