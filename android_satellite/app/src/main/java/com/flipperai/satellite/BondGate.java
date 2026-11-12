package com.flipperai.satellite;

/** Conservative bond requirement for the experimental AI characteristic.
 * A bond alone does not prove application identity or authorization; the
 * firmware GATT permissions must additionally require encrypted access.
 */
public final class BondGate {
    public static final int BONDED = 12; // BluetoothDevice.BOND_BONDED
    private BondGate() { }
    public static boolean canUseAiChannel(int bondState, boolean consent) {
        return consent && bondState == BONDED;
    }
}
