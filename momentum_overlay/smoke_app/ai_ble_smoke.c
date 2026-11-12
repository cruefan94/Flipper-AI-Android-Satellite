/* Compile-only, nonrelease internal-app linkage smoke test.
 * This is NOT the private Flipper AI application. No UI, no RF or GPIO action.
 * The experimental Bluetooth API is firmware internal and cannot be called
 * from a standard standalone FAP on stock Momentum.
 */
#include <furi.h>
#include <bt/bt_service/bt.h>

int32_t ai_ble_smoke_app(void* context) {
    (void)context;
    Bt* bt=furi_record_open(RECORD_BT);
    if(bt!=NULL) {
        size_t att_payload=0;
        uint32_t generation=0;
        /* Must be harmless without a verified encrypted AI session. */
        (void)bt_ai_satellite_status(bt,&att_payload,&generation);
        furi_record_close(RECORD_BT);
    }
    return 0;
}
