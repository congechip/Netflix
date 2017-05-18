/*
 * PACKAGEID: 41 53 43 4F 4E 3A   // ASCON:
 * APPLETID: 41 53 43 4F 4E 3A 50 04 47   // ASCON:PKG
 */
package applets;

import javacard.framework.*;

/**
 *
 * @author rajesh
 */
public class AsconApplet extends javacard.framework.Applet
{
    // MAIN INSTRUCTION CLASS
    final static byte CLA_SIMPLEAPPLET               = (byte) 0xB0;

    // INSTRUCTIONS
    final static byte INS_ASCONENCRYPTION              = (byte) 0x61;
    final static byte INS_ASCONDECRYPTION              = (byte) 0x62;

    final static short ARRAY_LENGTH                  = (short) 0xff;
    final static byte  AES_BLOCK_LENGTH              = (short) 0x16;

    final static short SW_BAD_TEST_DATA_LEN          = (short) 0x6680;
    final static short SW_KEY_LENGTH_BAD             = (short) 0x6715;
    final static short SW_CIPHER_DATA_LENGTH_BAD     = (short) 0x6710;
    final static short SW_OBJECT_NOT_AVAILABLE       = (short) 0x6711;
    final static short SW_BAD_PIN                    = (short) 0x6900;

    private   OwnerPIN       m_pin = null;
    //private   JavaCardAESforASCON aesCipher = null;       //AES engine for ASCON
    private   AsconCore       myascon = null;               //ASCON engine
    
    //ASCON Parameters
     byte[] AD = {(byte)0x02, (byte)0x05, (byte)0x09, (byte)0x09, (byte)0x00};
     short ADLEN = (short)AD.length;
     byte[] NSEC = {(byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x01};
     byte[] NPUB = {(byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02, (byte)0x02};
     byte[] KEY = {(byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08, (byte)0x09, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06};
    // TEMPORARRY ARRAY IN RAM
    private   byte           m_ramArray[] = null;
    // PERSISTENT ARRAY IN EEPROM
    private   byte           m_dataArray[] = null;

    /**
     * AezApplet constructor
     * Only this class's install method should create the applet object.
     */
    protected AsconApplet(byte[] buffer, short offset, byte length)
    {
        // data offset is used for application specific parameter.
        // initialization with default offset (AID offset).
        short dataOffset = offset;
        boolean isOP2 = false;

        if(length > 9) {
            // Install parameter detail. Compliant with OP 2.0.1.

            // | size | content
            // |------|---------------------------
            // |  1   | [AID_Length]
            // | 5-16 | [AID_Bytes]
            // |  1   | [Privilege_Length]
            // | 1-n  | [Privilege_Bytes] (normally 1Byte)
            // |  1   | [Application_Proprietary_Length]
            // | 0-m  | [Application_Proprietary_Bytes]

            // shift to privilege offset
            dataOffset += (short)( 1 + buffer[offset]);
            // finally shift to Application specific offset
            dataOffset += (short)( 1 + buffer[dataOffset]);

           // go to proprietary data
            dataOffset++;

            // PERSISTENT BUFFER IN EEPROM
            m_dataArray = new byte[ARRAY_LENGTH];
            Util.arrayFillNonAtomic(m_dataArray, (short) 0, ARRAY_LENGTH, (byte) 0);
            // TEMPORARY BUFFER USED FOR FAST OPERATION WITH MEMORY LOCATED IN RAM
            m_ramArray = JCSystem.makeTransientByteArray((short) 260, JCSystem.CLEAR_ON_DESELECT);

            //Create ASCON OBJECT
            myascon = new AsconCore();
            
            m_pin = new OwnerPIN((byte) 5, (byte) 4);
            m_pin.update(m_dataArray, (byte) 0, (byte) 4);

            // update flag
            isOP2 = true;

        } else {
           // ISOException.throwIt((short)(ISO7816.SW_WRONG_LENGTH + length));
          }

            // <PUT YOUR CREATION ACTION HERE>
            // register this instance
            register();
    }

    /**
     * Method installing the applet.
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the data parameter in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) throws ISOException
    {
        // applet  instance creation 
        new AsconApplet (bArray, bOffset, bLength);
    }

    /**
     * Select method returns true if applet selection is supported.
     * @return boolean status of selection.
     */
    public boolean select()
    {
        // <PUT YOUR SELECTION ACTION HERE>
        return true;
    }

    /**
     * Deselect method called by the system in the deselection process.
     */
    public void deselect()
    {
        // <PUT YOUR DESELECTION ACTION HERE>
        return;
    }

    /**
     * Method processing an incoming APDU.
     * @see APDU
     * @param apdu the incoming APDU
     * @exception ISOException with the response bytes defined by ISO 7816-4
     */
    public void process(APDU apdu) throws ISOException
    {
        // get the APDU buffer
        byte[] apduBuffer = apdu.getBuffer();
        
        // ignore the applet select command dispatched to the process
        if (selectingApplet())
            return;

        // APDU instruction parser
        if (apduBuffer[ISO7816.OFFSET_CLA] == CLA_SIMPLEAPPLET) {
            switch ( apduBuffer[ISO7816.OFFSET_INS] )
            {
                case INS_ASCONENCRYPTION: ASCONEncryption(apdu); break;
                case INS_ASCONDECRYPTION: ASCONDecryption(apdu); break;
                default :
                    // The INS code is not supported by the dispatcher
                    ISOException.throwIt( ISO7816.SW_INS_NOT_SUPPORTED ) ;
                break ;

            }
        }
        else ISOException.throwIt( ISO7816.SW_CLA_NOT_SUPPORTED);
    }
    
    
    public void ASCONEncryption(APDU apdu){
        byte[]    apdubuf = apdu.getBuffer();
        short     dataLen = apdu.setIncomingAndReceive();
            
        //Copy message to m_ramArray for encryption
        Util.arrayCopyNonAtomic(apdubuf, ISO7816.OFFSET_CDATA, m_ramArray, (short)0, dataLen);
            
        //ASCON Encryption
        myascon.crypto_aead_encrypt(apdubuf, (short)0, m_ramArray, dataLen, AD, ADLEN, NSEC, NPUB, KEY);
            
        // SEND OUTGOING BUFFER
        apdu.setOutgoingAndSend((short)0, (short)(dataLen+16));
    }

    
    public void ASCONDecryption(APDU apdu){
        byte[]    apdubuf = apdu.getBuffer();
        short     dataLen = apdu.setIncomingAndReceive();
        
        //Copy cipher to m_ramArray for decryption
        Util.arrayCopyNonAtomic(apdubuf, ISO7816.OFFSET_CDATA, m_ramArray, (short)0, dataLen);
        
        //ASCON Decryption
        myascon.crypto_aead_decrypt(apdubuf, (short)0, NSEC, m_ramArray, dataLen, AD, ADLEN, NPUB, KEY);
        
        // SEND OUTGOING BUFFER
        apdu.setOutgoingAndSend((short)0, (short)(dataLen-16));
    }
    
    
}
