/*
 * SatoChip SeedKeeper - Store your seeds on javacard
 * (c) 2020 by Toporin - 16DMCk4WUaHofchAhpMaQS4UPm4urcy2dN
 * Sources available on https://github.com/Toporin                   
 *                  
 *  
 * Based on the M.US.C.L.E framework:
 * see http://pcsclite.alioth.debian.org/musclecard.com/musclecard/
 * see https://github.com/martinpaljak/MuscleApplet/blob/d005f36209bdd7020bac0d783b228243126fd2f8/src/com/musclecard/CardEdge/CardEdge.java
 * 
 *  MUSCLE SmartCard Development
 *      Authors: Tommaso Cucinotta <cucinotta@sssup.it>
 *               David Corcoran    <corcoran@linuxnet.com>
 *      Description:      CardEdge implementation with JavaCard
 *      Protocol Authors: Tommaso Cucinotta <cucinotta@sssup.it>
 *                        David Corcoran <corcoran@linuxnet.com>
 *      
 * BEGIN LICENSE BLOCK
 * Copyright (C) 1999-2002 David Corcoran <corcoran@linuxnet.com>
 * Copyright (C) 2015-2019 Toporin 
 * All rights reserved.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * END LICENSE_BLOCK  
 */

package org.seedkeeper.applet;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.SystemException;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.CryptoException;
import javacard.security.Key;
import javacard.security.KeyAgreement;
import javacard.security.KeyBuilder;
//import javacard.security.KeyPair;
import javacard.security.Signature;
import javacard.security.MessageDigest;
import javacard.security.RandomData;
import javacardx.crypto.Cipher;

/**
 * Implements MUSCLE's Card Edge Specification.
 */
public class SeedKeeper extends javacard.framework.Applet { 

    /* constants declaration */

    /** 
     * VERSION HISTORY
     * PROTOCOL VERSION: changes that impact compatibility with the client side
     * APPLET VERSION:   changes with no impact on compatibility of the client
     */
    // 0.1-0.1: initial version
    private final static byte PROTOCOL_MAJOR_VERSION = (byte) 0; 
    private final static byte PROTOCOL_MINOR_VERSION = (byte) 1;
    private final static byte APPLET_MAJOR_VERSION = (byte) 0;
    private final static byte APPLET_MINOR_VERSION = (byte) 1;   

    // Maximum number of keys handled by the Cardlet
    //private final static byte MAX_NUM_KEYS = (byte) 16;
    // Maximum number of seeds handled by the Cardlet
    private final static byte MAX_NUM_SEEDS = (byte) 16; // TODO: set max?
    // Maximum number of PIN codes
    private final static byte MAX_NUM_PINS = (byte) 8; // TODO: set to 2?

    // Maximum size for the extended APDU buffer 
    private final static short EXT_APDU_BUFFER_SIZE = (short) 320;
    private final static short TMP_BUFFER_SIZE = (short) 256;
    private final static short TMP_BUFFER2_SIZE = (short) 32;
    
    // Minimum PIN size
    private final static byte PIN_MIN_SIZE = (byte) 4;
    // Maximum PIN size
    private final static byte PIN_MAX_SIZE = (byte) 16;// TODO: increase size?
    // PIN[0] initial value...
    private final static byte[] PIN_INIT_VALUE={(byte)'M',(byte)'u',(byte)'s',(byte)'c',(byte)'l',(byte)'e',(byte)'0',(byte)'0'};

    // code of CLA byte in the command APDU header
    private final static byte CardEdge_CLA = (byte) 0xB0;

    /****************************************
     * Instruction codes *
     ****************************************/

    // Applet initialization
    private final static byte INS_SETUP = (byte) 0x2A;

    // Keys' use and management
    // private final static byte INS_IMPORT_KEY = (byte) 0x32;
    // private final static byte INS_RESET_KEY = (byte) 0x33;
    // private final static byte INS_GET_PUBLIC_FROM_PRIVATE= (byte)0x35;

    // External authentication
    private final static byte INS_CREATE_PIN = (byte) 0x40; //TODO: remove?
    private final static byte INS_VERIFY_PIN = (byte) 0x42;
    private final static byte INS_CHANGE_PIN = (byte) 0x44;
    private final static byte INS_UNBLOCK_PIN = (byte) 0x46;
    private final static byte INS_LOGOUT_ALL = (byte) 0x60;

    // Status information
    private final static byte INS_LIST_PINS = (byte) 0x48;
    private final static byte INS_GET_STATUS = (byte) 0x3C;
    private final static byte INS_CARD_LABEL= (byte)0x3D;

    // HD wallet
    //private final static byte INS_BIP32_IMPORT_SEED= (byte) 0x6C;
    //private final static byte INS_BIP32_RESET_SEED= (byte) 0x77;
    private final static byte INS_BIP32_GET_AUTHENTIKEY= (byte) 0x73;
    //private final static byte INS_BIP32_SET_AUTHENTIKEY_PUBKEY= (byte)0x75;
    // private final static byte INS_BIP32_GET_EXTENDED_KEY= (byte) 0x6D;
    // private final static byte INS_BIP32_SET_EXTENDED_PUBKEY= (byte) 0x74;
    // private final static byte INS_SIGN_MESSAGE= (byte) 0x6E;
    // private final static byte INS_SIGN_SHORT_MESSAGE= (byte) 0x72;
    // private final static byte INS_SIGN_TRANSACTION= (byte) 0x6F;
    // private final static byte INS_PARSE_TRANSACTION = (byte) 0x71;
    // private final static byte INS_CRYPT_TRANSACTION_2FA = (byte) 0x76;
    // private final static byte INS_SET_2FA_KEY = (byte) 0x79;    
    // private final static byte INS_RESET_2FA_KEY = (byte) 0x78;
    // private final static byte INS_SIGN_TRANSACTION_HASH= (byte) 0x7A;

    // secure channel
    private final static byte INS_INIT_SECURE_CHANNEL = (byte) 0x81;
    private final static byte INS_PROCESS_SECURE_CHANNEL = (byte) 0x82;

    // SeedKeeper
    private final static byte INS_GENERATE_MASTERSEED= (byte)0xA0;
    private final static byte INS_GENERATE_2FA_SECRET= (byte)0xAE; 
    private final static byte INS_IMPORT_SECRET= (byte)0xA1;
    private final static byte INS_EXPORT_SECRET= (byte)0xA2;
    //private final static byte INS_IMPORT_PLAIN_SECRET= (byte)0xA1;
    //private final static byte INS_EXPORT_PLAIN_SECRET= (byte)0xA2;
    //private final static byte INS_IMPORT_ENCRYPTED_SECRET= (byte)0xA3;
    //private final static byte INS_EXPORT_ENCRYPTED_SECRET= (byte)0xA5;
    private final static byte INS_RESET_SECRET= (byte)0xA5;
    private final static byte INS_LIST_SECRET_HEADERS= (byte)0xA6;
    //private final static byte INS_IMPORT_SHAMIR_SHARED_SECRET= (byte)0xA7;
    //private final static byte INS_EXPORT_SHAMIR_SHARED_SECRET= (byte)0xA8;
    private final static byte INS_PRINT_LOGS= (byte)0xA9;
    private final static byte INS_EXPORT_AUTHENTIKEY= (byte) 0xAD;
    
    // Personalization PKI support
    private final static byte INS_IMPORT_PKI_CERTIFICATE = (byte) 0x92;
    private final static byte INS_EXPORT_PKI_CERTIFICATE = (byte) 0x93;
    private final static byte INS_SIGN_PKI_CSR = (byte) 0x94;
    private final static byte INS_EXPORT_PKI_PUBKEY = (byte) 0x98;
    private final static byte INS_LOCK_PKI = (byte) 0x99;
    private final static byte INS_CHALLENGE_RESPONSE_PKI= (byte) 0x9A;
    //private final static byte INS_IMPORT_PKI_PUBKEY = (byte) 0x90;
    //private final static byte INS_IMPORT_PKI_PRIVKEY = (byte) 0x91;
    //private final static byte INS_VERIFY_PKI_KEYPAIR = (byte) 0x97;
    //private final static byte INS_SET_ALLOWED_CARD_AID = (byte) 0x95;
    //private final static byte INS_GET_ALLOWED_CARD_AID = (byte) 0x96;
    
    // reset to factory settings
    private final static byte INS_RESET_TO_FACTORY = (byte) 0xFF;
    
    /****************************************
     *          Error codes                 *
     ****************************************/

    /** Entered PIN is not correct */
    private final static short SW_PIN_FAILED = (short)0x63C0;// includes number of tries remaining
    ///** DEPRECATED - Entered PIN is not correct */
    //private final static short SW_AUTH_FAILED = (short) 0x9C02;
    /** Required operation is not allowed in actual circumstances */
    private final static short SW_OPERATION_NOT_ALLOWED = (short) 0x9C03;
    /** Required setup is not not done */
    private final static short SW_SETUP_NOT_DONE = (short) 0x9C04;
    /** Required setup is already done */
    private final static short SW_SETUP_ALREADY_DONE = (short) 0x9C07;
    /** Required feature is not (yet) supported */
    final static short SW_UNSUPPORTED_FEATURE = (short) 0x9C05;
    /** Required operation was not authorized because of a lack of privileges */
    private final static short SW_UNAUTHORIZED = (short) 0x9C06;
    ///** Algorithm specified is not correct */
    //private final static short SW_INCORRECT_ALG = (short) 0x9C09;
    /** Logger error */
    //public final static short SW_LOGGER_ERROR = (short) 0x9C0A;

    /** There have been memory problems on the card */
    private final static short SW_NO_MEMORY_LEFT = ObjectManager.SW_NO_MEMORY_LEFT;
    /** DEPRECATED - Required object is missing */
    private final static short SW_OBJECT_NOT_FOUND= (short) 0x9C08;

    /** Incorrect P1 parameter */
    private final static short SW_INCORRECT_P1 = (short) 0x9C10;
    /** Incorrect P2 parameter */
    private final static short SW_INCORRECT_P2 = (short) 0x9C11;
    /** No more data available */
    private final static short SW_SEQUENCE_END = (short) 0x9C12;
    /** Invalid input parameter to command */
    private final static short SW_INVALID_PARAMETER = (short) 0x9C0F;

    // /** Eckeys initialized */
    // private final static short SW_ECKEYS_INITIALIZED_KEY = (short) 0x9C1A;

    /** Verify operation detected an invalid signature */
    private final static short SW_SIGNATURE_INVALID = (short) 0x9C0B;
    /** Operation has been blocked for security reason */
    private final static short SW_IDENTITY_BLOCKED = (short) 0x9C0C;
    /** For debugging purposes */
    private final static short SW_INTERNAL_ERROR = (short) 0x9CFF;
    // /** Very low probability error */
    // private final static short SW_BIP32_DERIVATION_ERROR = (short) 0x9C0E;
    // /** Incorrect initialization of method */
    private final static short SW_INCORRECT_INITIALIZATION = (short) 0x9C13;
    /** Bip32 seed is not initialized => this is actually the authentikey*/
    private final static short SW_BIP32_UNINITIALIZED_SEED = (short) 0x9C14;
    // /** Bip32 seed is already initialized (must be reset before change)*/
    // private final static short SW_BIP32_INITIALIZED_SEED = (short) 0x9C17;
    //** DEPRECATED - Bip32 authentikey pubkey is not initialized*/
    //private final static short SW_BIP32_UNINITIALIZED_AUTHENTIKEY_PUBKEY= (short) 0x9C16;
    // /** Incorrect transaction hash */
    // private final static short SW_INCORRECT_TXHASH = (short) 0x9C15;
    // /** 2FA already initialized*/
    // private final static short SW_2FA_INITIALIZED_KEY = (short) 0x9C18;
    // /** 2FA uninitialized*/
    // private final static short SW_2FA_UNINITIALIZED_KEY = (short) 0x9C19;
    
    /** Lock error**/
    private final static short SW_LOCK_ERROR= (short) 0x9C30;
    /** Export not allowed **/
    private final static short SW_EXPORT_NOT_ALLOWED= (short) 0x9C31;
    /** Secret data is too long for import **/
    private final static short SW_IMPORTED_DATA_TOO_LONG= (short) 0x9C32;
    /** Wrong HMAC when importing Secret through Secure import**/
    private final static short SW_SECURE_IMPORT_WRONG_MAC= (short) 0x9C33;
    /** Wrong Fingerprint when importing Secret through Secure import**/
    private final static short SW_SECURE_IMPORT_WRONG_FINGERPRINT= (short) 0x9C34;
    
    /** HMAC errors */
    static final short SW_HMAC_UNSUPPORTED_KEYSIZE = (short) 0x9c1E;
    static final short SW_HMAC_UNSUPPORTED_MSGSIZE = (short) 0x9c1F;

    /** Secure channel */
    private final static short SW_SECURE_CHANNEL_REQUIRED = (short) 0x9C20;
    private final static short SW_SECURE_CHANNEL_UNINITIALIZED = (short) 0x9C21;
    private final static short SW_SECURE_CHANNEL_WRONG_IV= (short) 0x9C22;
    private final static short SW_SECURE_CHANNEL_WRONG_MAC= (short) 0x9C23;
    
    /** PKI error */
    private final static short SW_PKI_ALREADY_LOCKED = (short) 0x9C40;
    //private final static short SW_KEYPAIR_MISMATCH = (short) 0x9C41;
    
    /** For instructions that have been deprecated*/
    private final static short SW_INS_DEPRECATED = (short) 0x9C26;
    /** CARD HAS BEEN RESET TO FACTORY */
    private final static short SW_RESET_TO_FACTORY = (short) 0xFF00;
    /** For debugging purposes 2 */
    private final static short SW_DEBUG_FLAG = (short) 0x9FFF;

    // KeyBlob Encoding in Key Blobs
    private final static byte BLOB_ENC_PLAIN = (byte) 0x00;

    // Cipher Operations admitted in ComputeCrypt()
    private final static byte OP_INIT = (byte) 0x01;
    private final static byte OP_PROCESS = (byte) 0x02;
    private final static byte OP_FINALIZE = (byte) 0x03;

    // JC API 2.2.2 does not define these constants:
    private final static byte ALG_ECDSA_SHA_256= (byte) 33;
    private final static byte ALG_EC_SVDP_DH_PLAIN= (byte) 3; //https://javacard.kenai.com/javadocs/connected/javacard/security/KeyAgreement.html#ALG_EC_SVDP_DH_PLAIN
    private final static byte ALG_EC_SVDP_DH_PLAIN_XY= (byte) 6; //https://docs.oracle.com/javacard/3.0.5/api/javacard/security/KeyAgreement.html#ALG_EC_SVDP_DH_PLAIN_XY
    private final static short LENGTH_EC_FP_256= (short) 256;

    /****************************************
     * Instance variables declaration *
     ****************************************/

    // PIN and PUK objects, allocated on demand
    private OwnerPIN[] pins, ublk_pins;
    
    //logger logs critical operations performed by the applet such as key export
    private Logger logger;
    private final static short LOGGER_NBRECORDS= (short) 100;
    
    private final static byte MAX_CARD_LABEL_SIZE = (byte) 64;
    private byte card_label_size= (byte)0x00;
    private byte[] card_label;
    
    // seeds data array
    // for each element: [id | mnemonic | passphrase | master_seed | encrypted_master_seed | label | status | settings ]
    // status: externaly/internaly generated, shamir, bip39 or electrum, 
    // settings: can be exported in clear, 
    private final static short OM_SIZE= (short) 0xFFF; //todo
    private ObjectManager om_secrets;
    private AESKey om_encryptkey; // used to encrypt sensitive data in object
    private Cipher om_aes128_ecb; // 
    private short om_nextid;
    private final static short OM_TYPE= 0x00;
    
    // type of secrets stored
    private final static byte SECRET_TYPE_MASTER_SEED = (byte) 0x10;
    private final static byte SECRET_TYPE_ENCRYPTED_MASTER_SEED = (byte) 0x20;
    private final static byte SECRET_TYPE_BIP39_MNEMONIC = (byte) 0x30;
    private final static byte SECRET_TYPE_ELECTRUM_MNEMONIC = (byte) 0x40;
    private final static byte SECRET_TYPE_SHAMIR_SECRET_SHARE = (byte) 0x50;
    private final static byte SECRET_TYPE_PRIVKEY = (byte) 0x60;
    private final static byte SECRET_TYPE_PUBKEY = (byte) 0x70;
    private final static byte SECRET_TYPE_KEY= (byte) 0x80;
    private final static byte SECRET_TYPE_PASSWORD= (byte) 0x90;
    private final static byte SECRET_TYPE_CERTIFICATE= (byte) 0xA0;
    private final static byte SECRET_TYPE_2FA= (byte) 0xB0;
    
    // export controls 
    private final static byte SECRET_EXPORT_ALLOWED = (byte) 0x01; //plain or encrypted
    private final static byte SECRET_EXPORT_SECUREONLY = (byte) 0x02; // only encrypted with authentikey
    private final static byte SECRET_EXPORT_AUTHENTICATED = (byte) 0x03; // TODO: only encrypted with certified authentikey
    private final static byte SECRET_EXPORT_FORBIDDEN = (byte) 0x04; // never allowed
    
    // origin
    private final static byte SECRET_ORIGIN_IMPORT_PLAIN= (byte) 0x01; 
    private final static byte SECRET_ORIGIN_IMPORT_SECURE = (byte) 0x02; 
    private final static byte SECRET_ORIGIN_ONCARD = (byte) 0x03; 
    
    // Offset
    private final static byte SECRET_OFFSET_TYPE=(byte) 0;
    private final static byte SECRET_OFFSET_ORIGIN=(byte) 1;
    private final static byte SECRET_OFFSET_EXPORT_CONTROL=(byte) 2;
    private final static byte SECRET_OFFSET_EXPORT_NBPLAIN=(byte) 3;
    private final static byte SECRET_OFFSET_EXPORT_NBSECURE=(byte) 4;
    private final static byte SECRET_OFFSET_EXPORT_COUNTER=(byte) 5; //(pubkey only) nb of time this pubkey has been used to export secret
    private final static byte SECRET_OFFSET_FINGERPRINT=(byte) 6; 
    private final static byte SECRET_OFFSET_RFU1=(byte) 10; 
    private final static byte SECRET_OFFSET_RFU2=(byte) 11; 
    private final static byte SECRET_OFFSET_LABEL_SIZE=(byte) 12;
    private final static byte SECRET_OFFSET_LABEL=(byte) 13;
    private final static byte SECRET_HEADER_SIZE=(byte) 13;
    private final static byte SECRET_FINGERPRINT_SIZE=(byte) 4;
        
    // label
    private final static byte MAX_LABEL_SIZE= (byte) 127; 
    private final static byte MAX_SEED_SIZE= (byte) 64; 
    private final static byte MIN_SEED_SIZE= (byte) 16;
    
    private final static byte AES_BLOCKSIZE= (byte)16;
    private final static byte SIZE_2FA= (byte)20;
    private static final byte[] SECRET_CST_SC = {'s','e','c','k','e','y', 's','e','c','m','a','c'};
    private byte[] secret_sc_buffer;
    private AESKey secret_sc_sessionkey;
    private Cipher secret_sc_aes128_cbc;
    private MessageDigest secret_sha256;
    
    //debug
    // common data_header: [ type(1b) | origin(1b) | export_control(1b) | nb_export_plain(1b) | nb_export_secure(1b) | expot_pubkey_counter(1b) | fingerprint (4b) | RFU(2b) | label_size(1b) | label ]
    // SECRET_TYPE_MASTER_SEED: [ size(1b) | seed_blob ]
    // SECRET_TYPE_ENCRYPTED_MASTER_SEED: [ size(1b) | seed_blob | passphrase_size(1b) | passphrase | e(1b) ]
    // SECRET_TYPE_BIP39_MNEMONIC: [mnemonic_size(1b) | mnemonic | passphrase_size(1b) | passphrase ]
    // SECRET_TYPE_ELECTRUM_MNEMONIC: [mnemonic_size(1b) | mnemonic | passphrase_size(1b) | passphrase ]
    // SECRET_TYPE_SHAMIR_SECRET_SHARE: [TODO]
    // SECRET_TYPE_PRIVKEY: [TODO]
    // SECRET_TYPE_PUBKEY: [keytype(1b) | keysize(1b) | key ]
    // SECRET_TYPE_KEY: [keytype(1b) | keysize(1b) | key]

    // Buffer for storing extended APDUs
    private byte[] recvBuffer; 
    private byte[] tmpBuffer; //used for hmac computation
    private byte[] tmpBuffer2; //used in securechannel
    
    /*
     * Logged identities: this is used for faster access control, so we don't
     * have to ping each PIN object
     */
    private short logged_ids;

    /* For the setup function - should only be called once */
    private boolean setupDone = false;

    // Multi-Step Install variables
    private short install_step = 0;

    // lock mechanism for multiple call to 
    private boolean lock_enabled = false;
    private byte lock_ins=(byte)0;
    private byte lock_lastop=(byte)0;
    private byte lock_transport_mode= (byte)0;
    private short lock_id=-1;
    private short lock_id_pubkey=-1;
    private short lock_recv_offset=(short)0;
    private short lock_data_size=(short)0;
    private short lock_data_remaining=(short)0;
    
    // shared cryptographic objects
    private RandomData randomData;
    private KeyAgreement keyAgreement;
    private Signature sigECDSA;
    //private Cipher aes128;
    private MessageDigest sha256;

    // reset to factory
    private byte[] reset_array;
    private final static byte MAX_RESET_COUNTER= (byte)5;
    private byte reset_counter=MAX_RESET_COUNTER;
    
    /*********************************************
     *  BIP32 Hierarchical Deterministic Wallet  *
     *********************************************/
    
    // seed derivation
    private static final byte[] BITCOIN_SEED = {'B','i','t','c','o','i','n',' ','s','e','e','d'};
    private static final byte[] BITCOIN_SEED2 = {'B','i','t','c','o','i','n',' ','s','e','e','d','2'};
    private static final short BIP32_KEY_SIZE= 32; // size of extended key and chain code is 256 bits
    // private static final byte MAX_BIP32_DEPTH = 10; // max depth in extended key from master (m/i is depth 1)

    /*********************************************
     *        Other data instances               *
     *********************************************/

    // secure channel
    private static final byte[] CST_SC = {'s','c','_','k','e','y', 's','c','_','m','a','c'};
    private boolean needs_secure_channel= true;
    private boolean initialized_secure_channel= false;
    private ECPrivateKey sc_ephemeralkey; 
    private AESKey sc_sessionkey;
    private Cipher sc_aes128_cbc;
    private byte[] sc_buffer;
    private static final byte OFFSET_SC_IV=0;
    private static final byte OFFSET_SC_IV_RANDOM=OFFSET_SC_IV;
    private static final byte OFFSET_SC_IV_COUNTER=12;
    private static final byte OFFSET_SC_MACKEY=16;
    private static final byte SIZE_SC_MACKEY=20;
    private static final byte SIZE_SC_IV= 16;
    private static final byte SIZE_SC_IV_RANDOM=12;
    private static final byte SIZE_SC_IV_COUNTER=SIZE_SC_IV-SIZE_SC_IV_RANDOM;
    private static final byte SIZE_SC_BUFFER=SIZE_SC_MACKEY+SIZE_SC_IV;

    //private ECPrivateKey bip32_authentikey; // key used to authenticate data
    
    // additional options
    private short option_flags;

    /*********************************************
     *               PKI objects                 *
     *********************************************/
    private static final byte[] PKI_CHALLENGE_MSG = {'C','h','a','l','l','e','n','g','e',':'};
    private boolean personalizationDone=false;
    private ECPrivateKey authentikey_private;
    private ECPublicKey authentikey_public;
    //private KeyPair authentikey_pair;
    private short authentikey_certificate_size=0;
    private byte[] authentikey_certificate;
    
    /****************************************
     * Methods *
     ****************************************/

    private SeedKeeper(byte[] bArray, short bOffset, byte bLength) {
        // FIXED: something should be done already here, not only with setup APDU

        /* If init pin code does not satisfy policies, internal error */
        if (!CheckPINPolicy(PIN_INIT_VALUE, (short) 0, (byte) PIN_INIT_VALUE.length))
            ISOException.throwIt(SW_INTERNAL_ERROR);

        ublk_pins = new OwnerPIN[MAX_NUM_PINS];
        pins = new OwnerPIN[MAX_NUM_PINS];

        // DONE: pass in starting PIN setting with instantiation
        /* Setting initial PIN n.0 value */
        pins[0] = new OwnerPIN((byte) 3, (byte) PIN_INIT_VALUE.length);
        pins[0].update(PIN_INIT_VALUE, (short) 0, (byte) PIN_INIT_VALUE.length);
        
        // reset to factory
        try {
            reset_array = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
        } catch (SystemException e) {
            ISOException.throwIt(SW_UNSUPPORTED_FEATURE);// unsupported feature => use a more recent card!
        }
        
        // Temporary working arrays
        try {
            tmpBuffer = JCSystem.makeTransientByteArray(TMP_BUFFER_SIZE, JCSystem.CLEAR_ON_DESELECT);
        } catch (SystemException e) {
            tmpBuffer = new byte[TMP_BUFFER_SIZE];
        }
        try {
            tmpBuffer2 = JCSystem.makeTransientByteArray(TMP_BUFFER2_SIZE, JCSystem.CLEAR_ON_DESELECT);
        } catch (SystemException e) {
            tmpBuffer2 = new byte[TMP_BUFFER2_SIZE];
        }
        // Initialize the extended APDU buffer
        try {
            // Try to allocate the extended APDU buffer on RAM memory
            recvBuffer = JCSystem.makeTransientByteArray(EXT_APDU_BUFFER_SIZE, JCSystem.CLEAR_ON_DESELECT);
        } catch (SystemException e) {
            // Allocate the extended APDU buffer on EEPROM memory
            // This is the fallback method, but its usage is really not
            // recommended as after ~ 100000 writes it will kill the EEPROM cells...
            recvBuffer = new byte[EXT_APDU_BUFFER_SIZE];
        }

        // shared cryptographic objects
        randomData = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        secret_sha256= MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
        sha256= MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);   
        sigECDSA= Signature.getInstance(ALG_ECDSA_SHA_256, false); 
        HmacSha160.init(tmpBuffer);
        try {
            keyAgreement = KeyAgreement.getInstance(ALG_EC_SVDP_DH_PLAIN_XY, false); 
        } catch (CryptoException e) {
            // TODO: remove if possible
            ISOException.throwIt(SW_UNSUPPORTED_FEATURE);// unsupported feature => use a more recent card!
        }

        //secure channel objects
        try {
            sc_buffer = JCSystem.makeTransientByteArray((short) SIZE_SC_BUFFER, JCSystem.CLEAR_ON_DESELECT);
        } catch (SystemException e) {
            sc_buffer = new byte[SIZE_SC_BUFFER];
        }
        try {
            secret_sc_buffer = JCSystem.makeTransientByteArray((short) SIZE_SC_BUFFER, JCSystem.CLEAR_ON_DESELECT);
        } catch (SystemException e) {
            secret_sc_buffer = new byte[SIZE_SC_BUFFER];
        }
        sc_sessionkey= (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false); // todo: make transient?
        sc_ephemeralkey= (ECPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PRIVATE, LENGTH_EC_FP_256, false);
        sc_aes128_cbc= Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false); 
        secret_sc_sessionkey= (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        secret_sc_aes128_cbc= Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);

        install_step = 1;

        // debug
        register();

    } // end of constructor

    private boolean complete_install() {
        // Secret objects manager
        om_secrets= new ObjectManager(OM_SIZE);
        randomData.generateData(recvBuffer, (short)0, (short)16);
        om_encryptkey= (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        om_encryptkey.setKey(recvBuffer, (short)0); // data must be exactly 16 bytes long
        om_aes128_ecb= Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);

        // logger
        logger= new Logger(LOGGER_NBRECORDS);

        // card label
        card_label= new byte[MAX_CARD_LABEL_SIZE];

        // perso PKI: generate public/private keypair
        authentikey_private= (ECPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PRIVATE, LENGTH_EC_FP_256, false);
        Secp256k1.setCommonCurveParameters(authentikey_private);
        authentikey_public= (ECPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PUBLIC, LENGTH_EC_FP_256, false);
        Secp256k1.setCommonCurveParameters(authentikey_public);
        //authentikey_pair= new KeyPair(authentikey_public, authentikey_private);
        //authentikey_pair.genKeyPair();
        randomData.generateData(recvBuffer, (short)0, BIP32_KEY_SIZE);
        authentikey_private.setS(recvBuffer, (short)0, BIP32_KEY_SIZE); //random value first
        keyAgreement.init(authentikey_private);
        keyAgreement.generateSecret(Secp256k1.SECP256K1, Secp256k1.OFFSET_SECP256K1_G, (short) 65, recvBuffer, (short)0); //pubkey in uncompressed form => silently fail after cap loaded
        authentikey_public.setW(recvBuffer, (short)0, (short)65);

        install_step = 2;
        return true;
    }


    public static void install(byte[] bArray, short bOffset, byte bLength) {
        SeedKeeper wal = new SeedKeeper(bArray, bOffset, bLength);
    }

    public boolean select() {
        /*
         * Application has been selected: Do session cleanup operation
         */
        LogOutAll();

        //todo: clear secure channel values?
        initialized_secure_channel=false;

        if (install_step < 2) {
            complete_install();
        }

        return true;
    }

    public void deselect() {
        LogOutAll();
    }

    public void process(APDU apdu) {
        // APDU object carries a byte array (buffer) to
        // transfer incoming and outgoing APDU header
        // and data bytes between card and CAD

        // At this point, only the first header bytes
        // [CLA, INS, P1, P2, P3] are available in
        // the APDU buffer.
        // The interface javacard.framework.ISO7816
        // declares constants to denote the offset of
        // these bytes in the APDU buffer

        if (selectingApplet())
            ISOException.throwIt(ISO7816.SW_NO_ERROR);

        byte[] buffer = apdu.getBuffer();
        // check SELECT APDU command
        if ((buffer[ISO7816.OFFSET_CLA] == 0) && (buffer[ISO7816.OFFSET_INS] == (byte) 0xA4))
            return;
        // verify the rest of commands have the
        // correct CLA byte, which specifies the
        // command structure
        if (buffer[ISO7816.OFFSET_CLA] != CardEdge_CLA)
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

        byte ins = buffer[ISO7816.OFFSET_INS];
        
        // Reset to factory 
        //    To trigger reset to factory, user must insert and remove card a fixed number of time, 
        //    without sending any other command than 1 reset in between
        if (ins == (byte) INS_RESET_TO_FACTORY){
            if (reset_array[0]==0){
                reset_counter--;
                reset_array[0]=(byte)1;
            }else{
                // if INS_RESET_TO_FACTORY is sent after any instruction, the reset process is aborted.
                reset_counter=MAX_RESET_COUNTER;
                ISOException.throwIt((short)(SW_RESET_TO_FACTORY + 0xFF));
            }
            if (reset_counter== 0) {
                reset_counter=MAX_RESET_COUNTER;
                resetToFactory();
                ISOException.throwIt(SW_RESET_TO_FACTORY);
            }
            ISOException.throwIt((short)(SW_RESET_TO_FACTORY + reset_counter));
        }
        else{
            reset_counter=MAX_RESET_COUNTER;
            reset_array[0]=(byte)1;
        }
        
        // prepare APDU buffer
        if (ins != INS_GET_STATUS){
            short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
            if (bytesLeft != apdu.setIncomingAndReceive())
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        // only 3 commands are allowed, the others must be wrapped in a secure channel command
        // the 3 commands are: get_status, initialize_secure_channel & process_secure_channel
        short sizeout=(short)0;
        if (ins == INS_GET_STATUS){
            sizeout= GetStatus(apdu, buffer);
            apdu.setOutgoingAndSend((short) 0, sizeout);
            return;
        }
        else if (ins == INS_INIT_SECURE_CHANNEL){
            sizeout= InitiateSecureChannel(apdu, buffer);
            apdu.setOutgoingAndSend((short) 0, sizeout);
            return;
        }
        else if (ins == INS_PROCESS_SECURE_CHANNEL){
            sizeout= ProcessSecureChannel(apdu, buffer);
            //todo: check if sizeout and buffer[ISO7816.OFFSET_LC] matches...
            //if sizeout>4, buffer[ISO7816.OFFSET_LC] should be equal to (sizeout-5)
            //todo: remove padding ? (it is actually not used)          
        }
        else if (needs_secure_channel){
            ISOException.throwIt(SW_SECURE_CHANNEL_REQUIRED);
        }

        // at this point, the encrypted content has been deciphered in the buffer
        ins = buffer[ISO7816.OFFSET_INS];
        if (!setupDone && (ins != INS_SETUP)){
            if (personalizationDone ||
                    ((ins != INS_VERIFY_PIN) 
                    && (ins != INS_EXPORT_PKI_PUBKEY)
                    && (ins != INS_IMPORT_PKI_CERTIFICATE)
                    && (ins != INS_SIGN_PKI_CSR)
                    && (ins != INS_LOCK_PKI)) ){
                ISOException.throwIt(SW_SETUP_NOT_DONE);
            } 
        }
        if (setupDone && (ins == INS_SETUP))
            ISOException.throwIt(SW_SETUP_ALREADY_DONE);

        // check lock: for some operations, the same command instruction must be called several times successively
        // We must ensure that it is indeed the case
        if ((lock_enabled) && lock_ins!= ins){
            resetLockException();
        }

        switch (ins) {
            case INS_SETUP:
                sizeout= setup(apdu, buffer);
                break;
            case INS_VERIFY_PIN:
                sizeout= VerifyPIN(apdu, buffer);
                break;
            case INS_CREATE_PIN:
                sizeout= CreatePIN(apdu, buffer);
                break;
            case INS_CHANGE_PIN:
                sizeout= ChangePIN(apdu, buffer);
                break;
            case INS_UNBLOCK_PIN:
                sizeout= UnblockPIN(apdu, buffer);
                break;
            case INS_LOGOUT_ALL:
                sizeout= LogOutAll();
                break;
            case INS_LIST_PINS:
                sizeout= ListPINs(apdu, buffer);
                break;
            case INS_GET_STATUS:
                sizeout= GetStatus(apdu, buffer);
                break;
            case INS_CARD_LABEL:
                sizeout= card_label(apdu, buffer);
                break;
            case INS_BIP32_GET_AUTHENTIKEY:
                sizeout= getBIP32AuthentiKey(apdu, buffer);
                break;
            case INS_GENERATE_MASTERSEED:
                sizeout= generateMasterseed(apdu, buffer);
                break;
            case INS_GENERATE_2FA_SECRET:
                sizeout= generate2FASecret(apdu, buffer);
                break;
            case INS_IMPORT_SECRET:
                sizeout= importSecret(apdu, buffer);
                break;
            case INS_EXPORT_SECRET:
                sizeout= exportSecret(apdu, buffer);
                break;
            case INS_RESET_SECRET:
                sizeout= resetSecret(apdu, buffer);
                break;
            case INS_LIST_SECRET_HEADERS:
                sizeout= listSecretHeaders(apdu, buffer);
                break;
            case INS_PRINT_LOGS:
                sizeout= printLogs(apdu, buffer);
                break;
            case INS_EXPORT_AUTHENTIKEY:
                sizeout= getAuthentikey(apdu, buffer);
                break;    
            //PKI
            case INS_EXPORT_PKI_PUBKEY:
                sizeout= export_PKI_pubkey(apdu, buffer);
                break;
            case INS_IMPORT_PKI_CERTIFICATE:
                sizeout= import_PKI_certificate(apdu, buffer);
                break;
            case INS_EXPORT_PKI_CERTIFICATE:
                sizeout= export_PKI_certificate(apdu, buffer);
                break;
            case INS_SIGN_PKI_CSR:
                sizeout= sign_PKI_CSR(apdu, buffer);
                break;
            case INS_LOCK_PKI:
                sizeout= lock_PKI(apdu, buffer);
                break;
            case INS_CHALLENGE_RESPONSE_PKI:
                sizeout= challenge_response_pki(apdu, buffer);
                break;
            // default
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }//end of switch

        // Prepare buffer for return
        if (sizeout==0){
            return;
        }
        else if ((ins == INS_GET_STATUS) || (ins == INS_INIT_SECURE_CHANNEL)) {
            apdu.setOutgoingAndSend((short) 0, sizeout);
        }
        else if (needs_secure_channel) { // encrypt response
            // buffer contains the data (sizeout)
            // for encryption, data is padded with PKCS#7
            short blocksize=(short)16;
            short padsize= (short) (blocksize - (sizeout%blocksize));

            Util.arrayCopy(buffer, (short)0, tmpBuffer, (short)0, sizeout);
            Util.arrayFillNonAtomic(tmpBuffer, sizeout, padsize, (byte)padsize);//padding
            Util.arrayCopy(sc_buffer, OFFSET_SC_IV, buffer, (short)0, SIZE_SC_IV);
            sc_aes128_cbc.init(sc_sessionkey, Cipher.MODE_ENCRYPT, sc_buffer, OFFSET_SC_IV, SIZE_SC_IV);
            short sizeoutCrypt=sc_aes128_cbc.doFinal(tmpBuffer, (short)0, (short)(sizeout+padsize), buffer, (short) (18));
            Util.setShort(buffer, (short)16, sizeoutCrypt);
            sizeout= (short)(18+sizeoutCrypt);
            //send back
            apdu.setOutgoingAndSend((short) 0, sizeout);
        }
        else {
            apdu.setOutgoingAndSend((short) 0, sizeout);
        }

    } // end of process method

    /** 
     * Setup APDU - initialize the applet and reserve memory
     * This is done only once during the lifetime of the applet
     * 
     * ins: INS_SETUP (0x2A) 
     * p1: 0x00
     * p2: 0x00
     * data: [default_pin_length(1b) | default_pin | 
     *        pin_tries0(1b) | ublk_tries0(1b) | pin0_length(1b) | pin0 | ublk0_length(1b) | ublk0 | 
     *        pin_tries1(1b) | ublk_tries1(1b) | pin1_length(1b) | pin1 | ublk1_length(1b) | ublk1 | 
     *        RFU(2b) | RFU(2b) | RFU(3b) |
     *        option_flags(2b - RFU) | 
     *        ]
     * where: 
     *      default_pin: {0x4D, 0x75, 0x73, 0x63, 0x6C, 0x65, 0x30, 0x30};
     *      pin_tries: max number of PIN try allowed before the corresponding PIN is blocked
     *      ublk_tries:  max number of UBLK(unblock) try allowed before the PUK is blocked
     *      option_flags: flags to define up to 16 additional options       
     * return: none
     */
    private short setup(APDU apdu, byte[] buffer) {
        personalizationDone=true;// perso PKI should not be modifiable once setup is done
        
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        short base = (short) (ISO7816.OFFSET_CDATA);
        byte numBytes = buffer[base++];
        bytesLeft--;

        OwnerPIN pin = pins[0];

        if (!CheckPINPolicy(buffer, base, numBytes))
            ISOException.throwIt(SW_INVALID_PARAMETER);

        byte triesRemaining = pin.getTriesRemaining();
        if (triesRemaining == (byte) 0x00)
            ISOException.throwIt(SW_IDENTITY_BLOCKED);
        if (!pin.check(buffer, base, numBytes))
            ISOException.throwIt((short)(SW_PIN_FAILED + triesRemaining - 1));

        base += numBytes;
        bytesLeft-=numBytes;

        byte pin_tries = buffer[base++];
        byte ublk_tries = buffer[base++];
        numBytes = buffer[base++];
        bytesLeft-=3;

        if (!CheckPINPolicy(buffer, base, numBytes))
            ISOException.throwIt(SW_INVALID_PARAMETER); 

        pins[0] = new OwnerPIN(pin_tries, PIN_MAX_SIZE);//TODO: new pin or update pin?
        pins[0].update(buffer, base, numBytes);

        base += numBytes;
        bytesLeft-=numBytes;
        numBytes = buffer[base++];
        bytesLeft--;

        if (!CheckPINPolicy(buffer, base, numBytes))
            ISOException.throwIt(SW_INVALID_PARAMETER);

        if (ublk_pins[0]==null)
            ublk_pins[0] = new OwnerPIN(ublk_tries, PIN_MAX_SIZE);
        ublk_pins[0].update(buffer, base, numBytes);

        base += numBytes;
        bytesLeft-=numBytes;

        pin_tries = buffer[base++];
        ublk_tries = buffer[base++];
        numBytes = buffer[base++];
        bytesLeft-=3;

        if (!CheckPINPolicy(buffer, base, numBytes))
            ISOException.throwIt(SW_INVALID_PARAMETER);

        if (pins[1]==null)
            pins[1] = new OwnerPIN(pin_tries, PIN_MAX_SIZE);
        pins[1].update(buffer, base, numBytes);

        base += numBytes;
        bytesLeft-=numBytes;
        numBytes = buffer[base++];
        bytesLeft--;

        if (!CheckPINPolicy(buffer, base, numBytes))
            ISOException.throwIt(SW_INVALID_PARAMETER);

        if (ublk_pins[1]==null)
            ublk_pins[1] = new OwnerPIN(ublk_tries, PIN_MAX_SIZE);
        ublk_pins[1].update(buffer, base, numBytes);
        base += numBytes;
        bytesLeft-=numBytes;

        short RFU= Util.getShort(buffer, base); // secmem_size deprecated => RFU
        base += (short) 2;
        RFU = Util.getShort(buffer, base); //mem_size deprecated => RFU
        base += (short) 2;
        bytesLeft-=4;

        RFU = buffer[base++]; //create_object_ACL deprecated => RFU
        RFU = buffer[base++]; //create_key_ACL deprecated => RFU
        RFU = buffer[base++]; //create_pin_ACL deprecated => RFU
        bytesLeft-=3;
        
        // parse options
        option_flags=0;
        if (bytesLeft>=2){
            option_flags = Util.getShort(buffer, base);
            base+=(short)2;
            bytesLeft-=(short)2;
        }
        
        logged_ids = 0x0000; // No identities logged in
        om_nextid= (short)0;
        setupDone = true;
        return (short)0;//nothing to return
    }

    /********** UTILITY FUNCTIONS **********/

    /**
     * Registers logout of an identity. This must be called anycase when a PIN
     * verification or external authentication fail
     */
    private void LogoutIdentity(byte id_nb) {
        logged_ids &= (short) ~(0x0001 << id_nb);
    }

    /** Checks if PIN policies are satisfied for a PIN code */
    private boolean CheckPINPolicy(byte[] pin_buffer, short pin_offset, byte pin_size) {
        if ((pin_size < PIN_MIN_SIZE) || (pin_size > PIN_MAX_SIZE))
            return false;
        return true;
    }

    private void resetLock(){
        //reset data
        Util.arrayFillNonAtomic(recvBuffer, (short)0, lock_recv_offset, (byte)0x00);
        // Release lock
        lock_ins= 0x00;
        lock_lastop= 0x00;
        lock_recv_offset= 0x00;
        lock_transport_mode= 0x00;
        lock_enabled = false;
    }

    private void resetLockException() {
        //reset data
        resetLock();
        // throws exception
        ISOException.throwIt(SW_LOCK_ERROR);
    }
    
    /** Erase all user data */
    private boolean resetToFactory(){
        
        //TODO        
        // logs
        // currently, we do NOT erase logs, but we add an entry for the reset
        logger.createLog(INS_RESET_TO_FACTORY, (short)-1, (short)-1, (short)0x0000 );
        
        // reset all secrets in store
        om_secrets.resetObjectManager(true);
        
        // reset card label
        card_label_size=0;
        Util.arrayFillNonAtomic(card_label, (short)0, (short)card_label.length, (byte)0);
        
        // setup
        pins[0].update(PIN_INIT_VALUE, (short) 0, (byte) PIN_INIT_VALUE.length);
        setupDone=false;
        
        // update log
        logger.updateLog(INS_RESET_TO_FACTORY, (short)-1, (short)-1, (short)0x9000 );
        
        return true;
    }
    
    /****************************************
     * APDU handlers *
     ****************************************/   
    
    /** 
     * This function generates a master seed randomly within the SeedKeeper
     * 
     * ins: 0xA0
     * p1: seed size in byte (between 16-64)
     * p2: export_rights
     * data: [ label_size(1b) | label  ]
     * return: [ id(2b) | fingerprint(4b) ]
     */
    private short generateMasterseed(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        // log operation
        logger.createLog(INS_GENERATE_MASTERSEED, (short)-1, (short)-1, (short)0x0000);
        
        byte seed_size= buffer[ISO7816.OFFSET_P1];
        if ((seed_size < MIN_SEED_SIZE) || (seed_size > MAX_SEED_SIZE) )
            ISOException.throwIt(SW_INCORRECT_P1);
    
        byte export_rights = buffer[ISO7816.OFFSET_P2];
        if ((export_rights < SECRET_EXPORT_ALLOWED) || (export_rights > SECRET_EXPORT_FORBIDDEN) )
            ISOException.throwIt(SW_INCORRECT_P2);
    
        short buffer_offset = ISO7816.OFFSET_CDATA;
        short recv_offset = (short)0;
        short label_size= Util.makeShort((byte) 0x00, buffer[buffer_offset]);
        buffer_offset++;
        if (label_size> MAX_LABEL_SIZE)
            ISOException.throwIt(SW_INVALID_PARAMETER);
    
        // common data_header: [ type(1b) | origin(1b) | export_control(1b) | nb_export_plain(1b) | nb_export_secure(1b) | export_pubkey_counter(1b) | fingerprint(4b) | label_size(1b) | label ]
        // SECRET_TYPE_MASTER_SEED: [ size(1b) | seed_blob ]
        recvBuffer[SECRET_OFFSET_TYPE]= SECRET_TYPE_MASTER_SEED;
        recvBuffer[SECRET_OFFSET_ORIGIN]= SECRET_ORIGIN_ONCARD;
        recvBuffer[SECRET_OFFSET_EXPORT_CONTROL]= export_rights;
        recvBuffer[SECRET_OFFSET_EXPORT_NBPLAIN]= (byte)0;
        recvBuffer[SECRET_OFFSET_EXPORT_NBSECURE]= (byte)0;
        recvBuffer[SECRET_OFFSET_EXPORT_COUNTER]= (byte)0;
        recvBuffer[SECRET_OFFSET_RFU1]= (byte)0;
        recvBuffer[SECRET_OFFSET_RFU2]= (byte)0;
        recvBuffer[SECRET_OFFSET_LABEL_SIZE]= (byte) (label_size & 0x7f);
        Util.arrayFillNonAtomic(recvBuffer, SECRET_OFFSET_FINGERPRINT, SECRET_FINGERPRINT_SIZE, (byte)0);
        Util.arrayCopyNonAtomic(buffer, buffer_offset, recvBuffer, SECRET_OFFSET_LABEL, label_size);
        recv_offset= (short) (SECRET_HEADER_SIZE + label_size);
        
        // generate seed
        buffer[(short)0]= seed_size;
        randomData.generateData(buffer,(short)(1), seed_size);
        //fingerprint
        sha256.reset();
        sha256.doFinal(buffer, (short)0, (short)(seed_size+1), buffer, (short)(seed_size+1));
        Util.arrayCopyNonAtomic(buffer, (short)(seed_size+1), recvBuffer, SECRET_OFFSET_FINGERPRINT, SECRET_FINGERPRINT_SIZE);
        //pad and encrypt seed for storage 
        short padsize= (short) (AES_BLOCKSIZE - ((seed_size+1)%AES_BLOCKSIZE) );
        Util.arrayFillNonAtomic(buffer, (short)(1+seed_size), padsize, (byte)padsize);//padding
        om_aes128_ecb.init(om_encryptkey, Cipher.MODE_ENCRYPT);
        short enc_size= om_aes128_ecb.doFinal(buffer, (short)0, (short)(1+seed_size+padsize), recvBuffer, recv_offset);
        recv_offset+=enc_size; //recv_offset+= seed_size;
        
        // Check if object exists
        while (om_secrets.exists(OM_TYPE, om_nextid)){
            om_nextid++;
        }
        short base= om_secrets.createObject(OM_TYPE, om_nextid, recv_offset);
        om_secrets.setObjectData(base, (short)0, recvBuffer, (short)0, recv_offset);
        
        // log operation (todo: fill log as soon as available)
        logger.updateLog(INS_GENERATE_MASTERSEED, om_nextid, (short)-1, (short)0x9000);
        
        // Fill the buffer
        Util.setShort(buffer, (short) 0, om_nextid);
        Util.arrayCopyNonAtomic(recvBuffer, SECRET_OFFSET_FINGERPRINT, buffer, (short)2, SECRET_FINGERPRINT_SIZE);
        om_nextid++;
        
        // TODO: sign id with authentikey?
        // Send response
        return (short)(2+SECRET_FINGERPRINT_SIZE);
    }
    
    /** 
     * This function generates a 2FA-secret randomly within the SeedKeeper
     * 
     * ins: AE
     * p1: 0x00
     * p2: export_rights
     * data: [ label_size(1b) | label  ]
     * return: [ id(2b) | fingerprint(4b) ]
     */
    private short generate2FASecret(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        // log operation
        logger.createLog(INS_GENERATE_2FA_SECRET, (short)-1, (short)-1, (short)0x0000);
        
        byte export_rights = buffer[ISO7816.OFFSET_P2];
        if ((export_rights < SECRET_EXPORT_ALLOWED) || (export_rights > SECRET_EXPORT_FORBIDDEN) )
            ISOException.throwIt(SW_INCORRECT_P2);
    
        short buffer_offset = ISO7816.OFFSET_CDATA;
        short recv_offset = (short)0;
        short label_size= Util.makeShort((byte) 0x00, buffer[buffer_offset]);
        buffer_offset++;
        if (label_size> MAX_LABEL_SIZE)
            ISOException.throwIt(SW_INVALID_PARAMETER);
    
        // common data_header: [ type(1b) | origin(1b) | export_control(1b) | nb_export_plain(1b) | nb_export_secure(1b) | export_pubkey_counter(1b) | fingerprint(4b) | label_size(1b) | label ]
        // SECRET_TYPE_2FA: [ size(1b) | 2FA_secret_blob ]
        recvBuffer[SECRET_OFFSET_TYPE]= SECRET_TYPE_2FA;
        recvBuffer[SECRET_OFFSET_ORIGIN]= SECRET_ORIGIN_ONCARD;
        recvBuffer[SECRET_OFFSET_EXPORT_CONTROL]= export_rights;
        recvBuffer[SECRET_OFFSET_EXPORT_NBPLAIN]= (byte)0;
        recvBuffer[SECRET_OFFSET_EXPORT_NBSECURE]= (byte)0;
        recvBuffer[SECRET_OFFSET_EXPORT_COUNTER]= (byte)0;
        recvBuffer[SECRET_OFFSET_RFU1]= (byte)0;
        recvBuffer[SECRET_OFFSET_RFU2]= (byte)0;
        recvBuffer[SECRET_OFFSET_LABEL_SIZE]= (byte) (label_size & 0x7f);
        Util.arrayFillNonAtomic(recvBuffer, SECRET_OFFSET_FINGERPRINT, SECRET_FINGERPRINT_SIZE, (byte)0);
        Util.arrayCopyNonAtomic(buffer, buffer_offset, recvBuffer, SECRET_OFFSET_LABEL, label_size);
        recv_offset= (short) (SECRET_HEADER_SIZE + label_size);
        
        // generate seed
        buffer[(short)0]= SIZE_2FA;
        randomData.generateData(buffer,(short)(1), SIZE_2FA);
        //fingerprint
        sha256.reset();
        sha256.doFinal(buffer, (short)0, (short)(SIZE_2FA+1), buffer, (short)(SIZE_2FA+1));
        Util.arrayCopyNonAtomic(buffer, (short)(SIZE_2FA+1), recvBuffer, SECRET_OFFSET_FINGERPRINT, SECRET_FINGERPRINT_SIZE);
        //pad and encrypt seed for storage 
        short padsize= (short) (AES_BLOCKSIZE - ((SIZE_2FA+1)%AES_BLOCKSIZE) );
        Util.arrayFillNonAtomic(buffer, (short)(1+SIZE_2FA), padsize, (byte)padsize);//padding
        om_aes128_ecb.init(om_encryptkey, Cipher.MODE_ENCRYPT);
        short enc_size= om_aes128_ecb.doFinal(buffer, (short)0, (short)(1+SIZE_2FA+padsize), recvBuffer, recv_offset);
        recv_offset+=enc_size; //recv_offset+= SIZE_2FA;
        
        // Check if object exists
        while (om_secrets.exists(OM_TYPE, om_nextid)){
            om_nextid++;
        }
        short base= om_secrets.createObject(OM_TYPE, om_nextid, recv_offset);
        om_secrets.setObjectData(base, (short)0, recvBuffer, (short)0, recv_offset);
        
        // log operation (todo: fill log as soon as available)
        logger.updateLog(INS_GENERATE_2FA_SECRET, om_nextid, (short)-1, (short)0x9000);
        
        // Fill the buffer
        Util.setShort(buffer, (short) 0, om_nextid);
        Util.arrayCopyNonAtomic(recvBuffer, SECRET_OFFSET_FINGERPRINT, buffer, (short)2, SECRET_FINGERPRINT_SIZE);
        om_nextid++;
        
        // TODO: sign id with authentikey?
        // Send response
        return (short)(2+SECRET_FINGERPRINT_SIZE);
    }
    
    /** 
     * This function imports a secret in plaintext/encrypted from host.
     * 
     * ins: 0xA1
     * p1: 0x01 (plain import) or 0x02 (secure import)
     * p2: operation (Init-Update-Final)
     * data:
     *      (init): [ header | (optional) id_pubkey(2b) | IV(16b)]
     *      (update):[chunk_size(2b) | data_blob ]
     *      (final): [chunk_size(2b) | data_blob | (if encrypted) hmac(20b) ]
     * return:
     *      (init/update): (none) 
     *      (final) [ id(2b) | fingerprint(4b) ]
     */
    private short importSecret(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        lock_transport_mode= buffer[ISO7816.OFFSET_P1];
        if (lock_transport_mode != SECRET_EXPORT_ALLOWED && lock_transport_mode != SECRET_EXPORT_SECUREONLY)
            ISOException.throwIt(SW_INVALID_PARAMETER);
        
        short bytes_left = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        short buffer_offset = ISO7816.OFFSET_CDATA;
        short recv_offset = (short)0;
        short data_size= (short)0;
        short enc_size=(short)0;
        short dec_size=(short)0;
        
        byte op = buffer[ISO7816.OFFSET_P2];
        switch (op) {
            case OP_INIT:
                // log operation to be updated later
                lock_id=(short)-1;
                lock_id_pubkey=(short)-1;
                logger.createLog(INS_IMPORT_SECRET, lock_id, lock_id_pubkey, (short)0x0000);
                
                // TODO: check lock?
                if (bytes_left<SECRET_HEADER_SIZE)
                    ISOException.throwIt(SW_INVALID_PARAMETER); 
                
                byte type= buffer[buffer_offset];
                buffer_offset++;
                buffer_offset++; // skip 'origin'
                byte export_rights= buffer[buffer_offset];
                buffer_offset++;
                if ((export_rights < SECRET_EXPORT_ALLOWED) || (export_rights > SECRET_EXPORT_FORBIDDEN) )
                    ISOException.throwIt(SW_INVALID_PARAMETER);
                buffer_offset+=7; // skip export_nb_plain, export_nb_secure, export_counter_pubkey and fingerprint
                byte RFU1= buffer[buffer_offset];
                buffer_offset++;
                byte RFU2= buffer[buffer_offset];
                buffer_offset++;
                short label_size= Util.makeShort((byte) 0x00, buffer[buffer_offset]);
                if (label_size> MAX_LABEL_SIZE)
                    ISOException.throwIt(SW_INVALID_PARAMETER);
                buffer_offset++;
                bytes_left-=SECRET_HEADER_SIZE;//5;
                if (bytes_left<label_size)
                    ISOException.throwIt(SW_INVALID_PARAMETER);
                short label_offset= buffer_offset;
                buffer_offset+=label_size;
                bytes_left-=label_size;
                 
                // load public key used for secure key eschange
                if (lock_transport_mode==SECRET_EXPORT_SECUREONLY){
                    if (bytes_left<2)
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    lock_id_pubkey= Util.getShort(buffer, buffer_offset);
                    buffer_offset+=2;
                    bytes_left-=2;
                    
                    if (bytes_left<SIZE_SC_IV)//IV
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    
                    // get pubkey
                    short base_pubkey= om_secrets.getBaseAddress(OM_TYPE, lock_id_pubkey);
                    if (base_pubkey==(short)0xFFFF){
                        resetLock();
                        ISOException.throwIt(SW_OBJECT_NOT_FOUND);
                    }
                    short obj_pubkey_size= om_secrets.getSizeFromAddress(base_pubkey);
                    om_secrets.getObjectData(base_pubkey, (short)0, recvBuffer, (short)0, obj_pubkey_size);
                    byte pubkey_type= recvBuffer[SECRET_OFFSET_TYPE];
                    if  (pubkey_type!=SECRET_TYPE_PUBKEY){
                        resetLock();
                        ISOException.throwIt(SW_INVALID_PARAMETER);//todo: better error code
                    }
                    
                    // get data 
                    short pubkey_label_size= Util.makeShort((byte)0, recvBuffer[SECRET_OFFSET_LABEL_SIZE]);
                    short data_offset= (short) (SECRET_HEADER_SIZE + pubkey_label_size);
                    // initialize cipher for pubkey decryption
                    om_aes128_ecb.init(om_encryptkey, Cipher.MODE_DECRYPT);
                    dec_size= om_aes128_ecb.doFinal(recvBuffer, data_offset, (short)80, recvBuffer, data_offset); //size should be 65+1+padding
                    short pubkey_size= recvBuffer[data_offset];
                    if (pubkey_size != 65){
                        //todo: check if compressed keys are supported
                        resetLock();
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    }
                    // compute shared static key 
                    keyAgreement.init(authentikey_private);        
                    keyAgreement.generateSecret(recvBuffer, (short)(data_offset+1), pubkey_size, recvBuffer, (short)0); //pubkey in uncompressed form
                    // derive secret_sessionkey & secret_mackey
                    HmacSha160.computeHmacSha160(recvBuffer, (short)1, (short)32, SECRET_CST_SC, (short)6, (short)6, recvBuffer, (short)33);
                    Util.arrayCopyNonAtomic(recvBuffer, (short)33, secret_sc_buffer, OFFSET_SC_MACKEY, SIZE_SC_MACKEY);
                    HmacSha160.computeHmacSha160(recvBuffer, (short)1, (short)32, SECRET_CST_SC, (short)0, (short)6, recvBuffer, (short)33);
                    secret_sc_sessionkey.setKey(recvBuffer,(short)33); // AES-128: 16-bytes key!!   
                    secret_sc_aes128_cbc.init(secret_sc_sessionkey, Cipher.MODE_DECRYPT, buffer, buffer_offset, SIZE_SC_IV);
                    // init hash for mac
                    secret_sha256.reset();
                    //secret_sha256.update(buffer, ISO7816.OFFSET_CDATA, (short)(SECRET_HEADER_SIZE+label_size));
                    secret_sha256.update(buffer, ISO7816.OFFSET_CDATA, (short)(SECRET_HEADER_SIZE-1)); //do not hash the label & label_size, so thay can be modified between export and import
                }
                
                // load (not so sensitive) header data from buffer
                recvBuffer[SECRET_OFFSET_TYPE]= type;
                recvBuffer[SECRET_OFFSET_ORIGIN]= lock_transport_mode; 
                recvBuffer[SECRET_OFFSET_EXPORT_CONTROL]= export_rights;
                recvBuffer[SECRET_OFFSET_EXPORT_NBPLAIN]= (byte)0;
                recvBuffer[SECRET_OFFSET_EXPORT_NBSECURE]= (byte)0;
                recvBuffer[SECRET_OFFSET_EXPORT_COUNTER]= (byte)0;
                Util.arrayFillNonAtomic(recvBuffer, SECRET_OFFSET_FINGERPRINT, SECRET_FINGERPRINT_SIZE, (byte)0); //todo: copy fingerprint from import?
                recvBuffer[SECRET_OFFSET_RFU1]= RFU1;
                recvBuffer[SECRET_OFFSET_RFU2]= RFU2;
                recvBuffer[SECRET_OFFSET_LABEL_SIZE]= (byte) (label_size & 0x7f);
                Util.arrayCopyNonAtomic(buffer, label_offset, recvBuffer, SECRET_OFFSET_LABEL, label_size);
                recv_offset+= (SECRET_HEADER_SIZE+label_size);
                
                // initialize cipher 
                om_aes128_ecb.init(om_encryptkey, Cipher.MODE_ENCRYPT);
                sha256.reset(); //for fingerprinting the secret
                
                //TODO: ensure atomicity?
                lock_enabled = true;
                lock_ins= INS_IMPORT_SECRET;
                lock_lastop= OP_INIT;
                lock_recv_offset= recv_offset;
                lock_data_size= (short)0;
                return (short)0;
                
            case OP_PROCESS:
                // TODO: check lock
                if ( (!lock_enabled) ||
                        (lock_ins!= INS_IMPORT_SECRET) ||
                        (lock_lastop!= OP_INIT && lock_lastop != OP_PROCESS))
                {
                    resetLockException();
                }

                if (bytes_left<2){
                    resetLock();// TODO: reset or not?
                    ISOException.throwIt(SW_INVALID_PARAMETER);}

                // load the new (sensitive) data
                recv_offset = lock_recv_offset;
                data_size= Util.getShort(buffer, buffer_offset);
                buffer_offset+=2;               
                bytes_left-=2;
                if (bytes_left<data_size){
                    resetLock();// TODO: reset or not?
                    ISOException.throwIt(SW_INVALID_PARAMETER);
                }
                
                
                if (lock_transport_mode==SECRET_EXPORT_SECUREONLY){
                    //hash the ciphertext to check hmac
                    secret_sha256.update(buffer, buffer_offset, data_size);
                    //decrypt secret
                    data_size= secret_sc_aes128_cbc.update(buffer, buffer_offset, data_size, buffer, buffer_offset);
                }
                
                // hash for fingerprinting & encrypt data
                sha256.update(buffer, buffer_offset, data_size);
                try{
                    enc_size= om_aes128_ecb.update(buffer, buffer_offset, data_size, recvBuffer, recv_offset);
                } catch (ArrayIndexOutOfBoundsException e){
                    resetLock();// TODO: reset or not?
                    ISOException.throwIt(SW_IMPORTED_DATA_TOO_LONG);}
                recv_offset+= enc_size;
                lock_data_size+=data_size;

                // TODO: ENSURE CONTINUITY OF OPERATIONS BETWEEN MULTIPLE APDU COMMANDS
                // Update lock state
                //lock_enabled = true;
                //lock_ins= INS_IMPORT_ENCRYPTED_SECRET;
                lock_lastop= OP_PROCESS;
                lock_recv_offset= recv_offset;
                return (short)0;

            case OP_FINALIZE:
                // check lock
                if ( (!lock_enabled) || 
                        (lock_ins!= INS_IMPORT_SECRET) ||
                        (lock_lastop!= OP_INIT && lock_lastop != OP_PROCESS))
                {
                    resetLockException();
                }

                if (bytes_left>=2){
                    // load the new (sensitive) data
                    buffer_offset = ISO7816.OFFSET_CDATA;
                    data_size= Util.getShort(buffer, buffer_offset);
                    buffer_offset+=2;
                    bytes_left-=2;  
                    
                    if (bytes_left<data_size){
                        resetLock();
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    }
                }else{
                    // no more data
                    data_size=(short)0;
                }
                
                short padsize=0;
                if (lock_transport_mode==SECRET_EXPORT_SECUREONLY){
                    //finalize hash the ciphertext and check hmac?
                    buffer_offset+=data_size;
                    bytes_left-=data_size;
                    if (bytes_left<1){
                        resetLock();
                        ISOException.throwIt(SW_INVALID_PARAMETER);}
                    short hmac_size= buffer[buffer_offset];
                    buffer_offset++;
                    bytes_left--;
                    if (hmac_size !=(short)20 || bytes_left<hmac_size){
                        resetLock();
                        ISOException.throwIt(SW_INVALID_PARAMETER);}
                    secret_sha256.doFinal(buffer, (short)(ISO7816.OFFSET_CDATA+2), data_size, buffer, (short)(buffer_offset+hmac_size) );
                    short sign_size=HmacSha160.computeHmacSha160(secret_sc_buffer, OFFSET_SC_MACKEY, SIZE_SC_MACKEY, buffer, (short)(buffer_offset+hmac_size), (short)32, buffer, (short)(buffer_offset+hmac_size+32) );
                    if(Util.arrayCompare(buffer, buffer_offset, buffer, (short)(buffer_offset+hmac_size+32), (short)20) != (byte)0){
                        resetLock();
                        ISOException.throwIt(SW_SECURE_IMPORT_WRONG_MAC);}
                    
                    buffer_offset=(short)(ISO7816.OFFSET_CDATA+2); //get back to offset with encrypted data
                    dec_size= secret_sc_aes128_cbc.doFinal(buffer, buffer_offset, data_size, buffer, buffer_offset);
                    
                    //already padded
                    padsize= buffer[(short)(buffer_offset+dec_size-1)];
                    data_size=(short)(dec_size-padsize);
                }
                else{
                    // padding
                    lock_data_size+=data_size;
                    padsize= (short) (AES_BLOCKSIZE - (lock_data_size%AES_BLOCKSIZE));
                    Util.arrayFillNonAtomic(buffer, (short)(buffer_offset+data_size), padsize, (byte)padsize);//padding
                }
                
                // finalize encrypt data
                recv_offset= lock_recv_offset;
                try{
                    enc_size= om_aes128_ecb.doFinal(buffer, buffer_offset, (short)(data_size+padsize), recvBuffer, recv_offset);
                } catch (ArrayIndexOutOfBoundsException e){
                    resetLock();// TODO: reset or not?
                    ISOException.throwIt(SW_IMPORTED_DATA_TOO_LONG);}
                recv_offset+=enc_size;
                // finalize hash to fingerprint
                sha256.doFinal(buffer, buffer_offset, data_size, buffer, (short)0);
                Util.arrayCopyNonAtomic(buffer, (short)0, recvBuffer, SECRET_OFFSET_FINGERPRINT, SECRET_FINGERPRINT_SIZE);
                
                // save to next available object
                // Check if object exists
                while (om_secrets.exists(OM_TYPE, om_nextid)){
                    om_nextid++;
                }
                short base= om_secrets.createObject(OM_TYPE, om_nextid, recv_offset);
                om_secrets.setObjectData(base, (short)0, recvBuffer, (short)0, recv_offset);
                
                // log operation
                logger.updateLog(INS_IMPORT_SECRET, om_nextid, lock_id_pubkey, (short)0x9000);
                
                // Fill the R-APDU buffer
                Util.setShort(buffer, (short) 0, om_nextid);
                om_nextid++;
                Util.arrayCopyNonAtomic(recvBuffer, SECRET_OFFSET_FINGERPRINT, buffer, (short)2, SECRET_FINGERPRINT_SIZE);
                
                // Release lock & send response
                lock_enabled = false;
                lock_ins= 0x00;
                lock_lastop= 0x00;
                lock_recv_offset= 0x00;
                lock_data_size= 0x00;
                lock_transport_mode= 0x00;
                return (short)(2+SECRET_FINGERPRINT_SIZE);

            default:
                ISOException.throwIt(SW_INCORRECT_P2);
        } // switch(op) 

        // Send default response
        return (short)0;    
    }
    ////////////////
    
    /** 
     * This function exports a secret in plaintext to the host.
     * Data is encrypted during transport through the Secure Channel
     * but the host has access to the data in plaintext.
     * 
     * ins: 0xA2
     * p1: 0x01 (plain export) or 0x02 (secure export)
     * p2: operation (Init-Update)
     * data: [ id(2b) | id_pubkey(2b) ]
     * return: 
     *      (init):[ header | IV(16b) ]
     *      (next):[data_blob_size(2b) | data_blob ]
     *      (last):[data_blob_size(2b) | data_blob | sig_size(1b) | authentikey_sig] if plain export
     *             [data_blob_size(2b) | data_blob | hmac_size(1b) | hmac(20b)] if secure export 
     */
    private short exportSecret(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        lock_transport_mode= buffer[ISO7816.OFFSET_P1];
        if (lock_transport_mode != SECRET_EXPORT_ALLOWED && lock_transport_mode != SECRET_EXPORT_SECUREONLY)
            ISOException.throwIt(SW_INVALID_PARAMETER);
        
        short bytes_left = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        short buffer_offset = ISO7816.OFFSET_CDATA;
        short recv_offset = (short)0;
        short dec_size=(short)0;
        short enc_size=(short)0;
        short chunk_size=(short)128; // should be multiple of 16 // TODO: make static final
        short label_size=(short)0;
        
        byte op = buffer[ISO7816.OFFSET_P2];
        switch (op) {
            case OP_INIT: // first request
                // set lock?
                lock_enabled = true;
                lock_ins= INS_EXPORT_SECRET;
                lock_lastop= OP_INIT;
                lock_data_remaining= (short)0;
                lock_recv_offset= (short)0;
                lock_id_pubkey= (short)-1;
                
                // get id
                if (bytes_left<2){
                    resetLock();// TODO: reset or not?
                    ISOException.throwIt(SW_INVALID_PARAMETER);}
                buffer_offset = ISO7816.OFFSET_CDATA;
                lock_id= Util.getShort(buffer, buffer_offset);
                buffer_offset+=2;
                
                if (lock_transport_mode==SECRET_EXPORT_SECUREONLY){
                    if (bytes_left<4){
                        resetLock();// TODO: reset or not?
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    }
                    lock_id_pubkey= Util.getShort(buffer, buffer_offset);
                    
                    // get pubkey
                    short base_pubkey= om_secrets.getBaseAddress(OM_TYPE, lock_id_pubkey);
                    if (base_pubkey==(short)0xFFFF){
                        resetLock();
                        ISOException.throwIt(SW_OBJECT_NOT_FOUND);
                    }
                    
                    short obj_pubkey_size= om_secrets.getSizeFromAddress(base_pubkey);
                    om_secrets.getObjectData(base_pubkey, (short)0, recvBuffer, (short)0, obj_pubkey_size);
                    byte pubkey_type= recvBuffer[SECRET_OFFSET_TYPE];
                    if  (pubkey_type!=SECRET_TYPE_PUBKEY){
                        //todo: check if compressed keys are supported
                        resetLock();
                        ISOException.throwIt(SW_INVALID_PARAMETER);//todo: better error code 
                    }
                    // update export_pubkey_counter in object
                    recvBuffer[SECRET_OFFSET_EXPORT_COUNTER]+=1;
                    om_secrets.setObjectByte(base_pubkey, SECRET_OFFSET_EXPORT_COUNTER, recvBuffer[SECRET_OFFSET_EXPORT_COUNTER]);
                    
                    // get data 
                    label_size= Util.makeShort((byte)0, recvBuffer[SECRET_OFFSET_LABEL_SIZE]);
                    short data_offset= (short) (SECRET_HEADER_SIZE + label_size);
                    // initialize cipher for pubkey decryption
                    om_aes128_ecb.init(om_encryptkey, Cipher.MODE_DECRYPT);
                    dec_size= om_aes128_ecb.doFinal(recvBuffer, data_offset, (short)80, recvBuffer, data_offset); //size should be 65+1+padding
                    short pubkey_size= recvBuffer[data_offset];
                    if (pubkey_size != 65){
                        //todo: check if compressed keys are supported
                        resetLock();
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    }
                    
                    // compute shared static key 
                    keyAgreement.init(authentikey_private);        
                    keyAgreement.generateSecret(recvBuffer, (short)(data_offset+1), (short) 65, recvBuffer, (short)0); //pubkey in uncompressed form
                    // derive secret_sessionkey & secret_mackey
                    HmacSha160.computeHmacSha160(recvBuffer, (short)1, (short)32, SECRET_CST_SC, (short)6, (short)6, recvBuffer, (short)33);
                    Util.arrayCopyNonAtomic(recvBuffer, (short)33, secret_sc_buffer, OFFSET_SC_MACKEY, SIZE_SC_MACKEY);
                    HmacSha160.computeHmacSha160(recvBuffer, (short)1, (short)32, SECRET_CST_SC, (short)0, (short)6, recvBuffer, (short)33);
                    secret_sc_sessionkey.setKey(recvBuffer,(short)33); // AES-128: 16-bytes key!!   
                    randomData.generateData(secret_sc_buffer, OFFSET_SC_IV, SIZE_SC_IV);
                    secret_sc_aes128_cbc.init(secret_sc_sessionkey, Cipher.MODE_ENCRYPT, secret_sc_buffer, OFFSET_SC_IV, SIZE_SC_IV);
                }
                
                // log operation to be updated later
                logger.createLog(INS_EXPORT_SECRET, lock_id, lock_id_pubkey, (short)0x0000);
                
                // copy to recvBuffer
                short base= om_secrets.getBaseAddress(OM_TYPE, lock_id);
                if (base==(short)0xFFFF){
                    resetLock();
                    logger.updateLog(INS_EXPORT_SECRET, lock_id, lock_id_pubkey, SW_OBJECT_NOT_FOUND);
                    ISOException.throwIt(SW_OBJECT_NOT_FOUND);
                }
                short obj_size= om_secrets.getSizeFromAddress(base);
                om_secrets.getObjectData(base, (short)0, recvBuffer, (short)0, obj_size);
                // update export_nb in object
                if (lock_transport_mode== SECRET_EXPORT_ALLOWED){
                    // check export rights
                    if (recvBuffer[SECRET_OFFSET_EXPORT_CONTROL]!=SECRET_EXPORT_ALLOWED){
                        resetLock();
                        logger.updateLog(INS_EXPORT_SECRET, lock_id, lock_id_pubkey, SW_EXPORT_NOT_ALLOWED);
                        ISOException.throwIt(SW_EXPORT_NOT_ALLOWED);
                    }
                    recvBuffer[SECRET_OFFSET_EXPORT_NBPLAIN]+=1; 
                    om_secrets.setObjectByte(base, SECRET_OFFSET_EXPORT_NBPLAIN, recvBuffer[SECRET_OFFSET_EXPORT_NBPLAIN]);
                }
                else{
                    recvBuffer[SECRET_OFFSET_EXPORT_NBSECURE]+=1; 
                    om_secrets.setObjectByte(base, SECRET_OFFSET_EXPORT_NBSECURE, recvBuffer[SECRET_OFFSET_EXPORT_NBSECURE]);   
                }
                
                // copy id & header to buffer
                Util.setShort(buffer, (short)0, lock_id);
                label_size= Util.makeShort((byte)0, recvBuffer[SECRET_OFFSET_LABEL_SIZE]);
                Util.arrayCopyNonAtomic(recvBuffer, (short)0, buffer, (short)2, (short)(SECRET_HEADER_SIZE+label_size));
                recv_offset= (short)(SECRET_HEADER_SIZE+label_size);
                lock_recv_offset= recv_offset;
                lock_data_remaining= (short)(obj_size-recv_offset);
                // save IV
                if (lock_transport_mode== SECRET_EXPORT_SECUREONLY){
                    Util.arrayCopyNonAtomic(secret_sc_buffer, OFFSET_SC_IV, buffer,(short)(2+SECRET_HEADER_SIZE+label_size), SIZE_SC_IV);
                }
                
                // initialize cipher & signature/hash for next phases
                om_aes128_ecb.init(om_encryptkey, Cipher.MODE_DECRYPT);
                if (lock_transport_mode==SECRET_EXPORT_SECUREONLY){
                    secret_sha256.reset();
                    //secret_sha256.update(buffer, (short)2, (short)(SECRET_HEADER_SIZE+label_size));
                    secret_sha256.update(buffer, (short)2, (short)(SECRET_HEADER_SIZE-1)); //do not hash label & label_size => may be changed during import
                }else{
                    sigECDSA.init(authentikey_private, Signature.MODE_SIGN);
                    sigECDSA.update(buffer, (short)0, (short)(2+SECRET_HEADER_SIZE+label_size));
                }
                // the client can recover full public-key from the signature or
                // by guessing the compression value () and verifying the signature... 
                // buffer= [id(2b) | type(1b) | export_control(1b) | nb_export_plain(1b) | nb_export_secure(1b) | label_size(1b) | label | sigsize(2) | sig]
                if (lock_transport_mode== SECRET_EXPORT_SECUREONLY){
                    return (short)(2+SECRET_HEADER_SIZE+label_size+SIZE_SC_IV);
                }else{
                    return (short)(2+SECRET_HEADER_SIZE+label_size);
                }
                
            case OP_PROCESS: // following requests
                // check lock
                if ( (lock_ins!= INS_EXPORT_SECRET) ||
                     (lock_lastop!= OP_INIT && lock_lastop != OP_PROCESS))
                {
                    resetLockException(); //TODO: log error?
                }
                
                // decrypt & export data chunk by chunk
                if (lock_data_remaining>chunk_size){
                    
                    dec_size= om_aes128_ecb.update(recvBuffer, lock_recv_offset, chunk_size, buffer, (short)2);
                    Util.setShort(buffer, (short)(0), dec_size);
                    
                    if (lock_transport_mode==SECRET_EXPORT_SECUREONLY){
                        // reencrypt with shared export key
                        enc_size= secret_sc_aes128_cbc.update(buffer, (short)2, chunk_size, buffer, (short)2);
                        Util.setShort(buffer, (short)(0), enc_size);
                        // assert (enc_size == dec_size)
                        // hashing for mac
                        secret_sha256.update(buffer, (short) 2, enc_size);
                    }else{
                        // update sign with authentikey
                        sigECDSA.update(buffer, (short)2, dec_size);
                    }
                    
                    lock_recv_offset+= chunk_size;
                    lock_data_remaining-=chunk_size;
                    
                    // buffer= [data_size(2b) | data_chunk]
                    return (short)(2+dec_size);
                
                //finalize last chunk
                }else{ 
                    
                    dec_size= om_aes128_ecb.doFinal(recvBuffer, lock_recv_offset, lock_data_remaining, buffer, (short)2);
                    short sign_size=0;
                    if (lock_transport_mode==SECRET_EXPORT_SECUREONLY){
                        // finalize reencryption with shared key
                        dec_size= secret_sc_aes128_cbc.doFinal(buffer, (short)2, dec_size, buffer, (short)2);
                        Util.setShort(buffer, (short)(0), dec_size);
                        //todo: assert (dec_size==enc_size)
                        
                        // hash then hmac
                        sign_size=secret_sha256.doFinal(buffer, (short) 2, dec_size, buffer, (short)(2+dec_size+2) );
                        sign_size=HmacSha160.computeHmacSha160(secret_sc_buffer, OFFSET_SC_MACKEY, SIZE_SC_MACKEY, buffer, (short)(2+dec_size+2), sign_size, buffer, (short)(2+dec_size+2) );
                        Util.setShort(buffer, (short)(2+dec_size), sign_size);
                    }
                    else{
                        //remove padding
                        byte padsize= buffer[(short)(2+dec_size-1)];
                        dec_size-=padsize;
                        Util.setShort(buffer, (short)(0), dec_size);
                        
                        // finalize sign with authentikey
                        sign_size= sigECDSA.sign(buffer, (short)2, dec_size, buffer, (short)(2+dec_size+2));
                        Util.setShort(buffer, (short)(2+dec_size), sign_size);
                    }
                    
                    //Util.setShort(buffer, (short)(0), dec_size);
                    lock_recv_offset+= lock_data_remaining;
                    lock_data_remaining=(short)0;
                                        
                    // log operation to be updated later
                    logger.updateLog(INS_EXPORT_SECRET, lock_id, lock_id_pubkey, (short)0x9000);
                    
                    // update/finalize lock
                    Util.arrayFillNonAtomic(recvBuffer, (short)0, lock_recv_offset, (byte)0x00);
                    lock_ins= (byte)0x00;
                    lock_lastop= (byte)0x00;
                    lock_id=(short)-1;
                    lock_id_pubkey=(short)-1;
                    lock_transport_mode= (byte)0;
                    lock_recv_offset=(short)0;
                    lock_enabled = false;
                    
                    // the client can recover full public-key from the signature or
                    // by guessing the compression value () and verifying the signature... 
                    // buffer= [data_size(2b) | data_chunk | sigsize(2) | sig]
                    return (short)(2+dec_size+2+sign_size);                
                }
            default:
                resetLock();
                ISOException.throwIt(SW_INCORRECT_P2);
        }// end switch   
        
        return (short)(0); // should never happen
    }// end exportSecret
            
    /** 
     * This function list all the objects stored in secure memory
     * Only the header data of each object is returned.
     * The sensitive data (which is encrypted) is not returned.
     * This function must be initially called with the INIT option. 
     * The function only returns one object information at a time and must be
     * called in repetition until SW_SUCCESS is returned with no further data.
     * Applications cannot rely on any special ordering of the sequence of returned objects. 
     * 
     * ins: 0xA6
     * p1: 0x00 
     * p2: OP_INIT (reset and get first entry) or OP_PROCESS (next entry)
     * data: (none)
     * return: [object_id(2b) | type(1b) | export_control(1b) | nb_export_plain(1b) | nb_export_secure(1b) | label_size(1b) | label ]
     */
    private short listSecretHeaders(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        short base=(short)0;
        short labelsize=(short)0;
        if (buffer[ISO7816.OFFSET_P2] == OP_INIT){
            base = om_secrets.getFirstRecord();
        }
        else if (buffer[ISO7816.OFFSET_P2] == OP_PROCESS){
            base = om_secrets.getNextRecord();
        }
        else{
            ISOException.throwIt(SW_INCORRECT_P2);
        }
        if (base==(short)0xFFFF)
            ISOException.throwIt(SW_SEQUENCE_END);
        
        short id= om_secrets.getIdFromAddress(base);
        Util.setShort(buffer, (short)0, id);
        labelsize= Util.makeShort((byte)0, om_secrets.getObjectByte(base,SECRET_OFFSET_LABEL_SIZE));
        om_secrets.getObjectData(base, (short)0, buffer, (short)2, (short)(SECRET_HEADER_SIZE+labelsize));
        
        //TODO: sign with authentikey 
        return (short)(2+SECRET_HEADER_SIZE+labelsize);
    }

    /** 
     * This function returns the logs stored in the card
     * 
     * This function must be initially called with the INIT option. 
     * The function only returns one object information at a time and must be
     * called in repetition until SW_SUCCESS is returned with no further data.
     * Log are returned starting with the most recent log first. 
     * 
     * ins: 0xA9
     * p1: 0x00 
     * p2: OP_INIT (reset and get first entry) or OP_PROCESS (next entry)
     * data: (none)
     * return: 
     *      OP_INIT: [nbtotal_logs(2b) | nbavail_logs(2b)]
     *      OP_PROCESS: [logs(7b)]
     */
    private short printLogs(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        short buffer_offset=(short)0;
        if (buffer[ISO7816.OFFSET_P2] == OP_INIT){
            boolean is_log= logger.getFirstRecord(buffer, buffer_offset);
            if (is_log)
                return (short)(4+Logger.LOG_SIZE);
            else
                return (short)4;        
        }
        else if (buffer[ISO7816.OFFSET_P2] == OP_PROCESS){
            while(logger.getNextRecord(buffer, buffer_offset)){
                buffer_offset+=Logger.LOG_SIZE;
                if (buffer_offset>=128)
                    break;
            }
            return buffer_offset;
        }
        else{
            ISOException.throwIt(SW_INCORRECT_P2);
        }
        return buffer_offset;
    }
    
    /** 
     * This function reset a secret object in memory.
     * TODO: evaluate security implications!
     * 
     * ins: 0xA5
     * p1: 0
     * p2: 0
     * data: [ id(2b) ]
     * return: (none)    
     */
    private short resetSecret(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        // currently not supported
        ISOException.throwIt(SW_UNSUPPORTED_FEATURE);
        
        return (short)0;
    }// end resetSecret
    
    /** 
     * This function creates a PIN with parameters specified by the P1, P2 and DATA
     * values. P2 specifies the maximum number of consecutive unsuccessful
     * verifications before the PIN blocks. PIN can be created only if one of the logged identities
     * allows it. 
     * 
     * ins: 0x40
     * p1: PIN number (0x00-0x07)
     * p2: max attempt number
     * data: [PIN_size(1b) | PIN | UBLK_size(1b) | UBLK] 
     * return: none
     */
    private short CreatePIN(APDU apdu, byte[] buffer) {
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);

        byte pin_nb = buffer[ISO7816.OFFSET_P1];
        byte num_tries = buffer[ISO7816.OFFSET_P2];

        if ((pin_nb < 0) || (pin_nb >= MAX_NUM_PINS) || (pins[pin_nb] != null))
            ISOException.throwIt(SW_INCORRECT_P1);
        /* Allow pin lengths > 127 (useful at all ?) */
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        // At least 1 character for PIN and 1 for unblock code (+ lengths)
        if (bytesLeft < 4)
            ISOException.throwIt(SW_INVALID_PARAMETER);
        byte pin_size = buffer[ISO7816.OFFSET_CDATA];
        if (bytesLeft < (short) (1 + pin_size + 1))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        if (!CheckPINPolicy(buffer, (short) (ISO7816.OFFSET_CDATA + 1), pin_size))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        byte ucode_size = buffer[(short) (ISO7816.OFFSET_CDATA + 1 + pin_size)];
        if (bytesLeft != (short) (1 + pin_size + 1 + ucode_size))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        if (!CheckPINPolicy(buffer, (short) (ISO7816.OFFSET_CDATA + 1 + pin_size + 1), ucode_size))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        pins[pin_nb] = new OwnerPIN(num_tries, PIN_MAX_SIZE);
        pins[pin_nb].update(buffer, (short) (ISO7816.OFFSET_CDATA + 1), pin_size);
        ublk_pins[pin_nb] = new OwnerPIN((byte) 3, PIN_MAX_SIZE);
        // Recycle variable pin_size
        pin_size = (byte) (ISO7816.OFFSET_CDATA + 1 + pin_size + 1);
        ublk_pins[pin_nb].update(buffer, pin_size, ucode_size);

        return (short)0;
    }

    /** 
     * This function verifies a PIN number sent by the DATA portion. The length of
     * this PIN is specified by the value contained in P3.
     * Multiple consecutive unsuccessful PIN verifications will block the PIN. If a PIN
     * blocks, then an UnblockPIN command can be issued.
     * 
     * ins: 0x42
     * p1: PIN number (0x00-0x07)
     * p2: 0x00
     * data: [PIN] 
     * return: none (throws an exception in case of wrong PIN)
     */
    private short VerifyPIN(APDU apdu, byte[] buffer) {
        byte pin_nb = buffer[ISO7816.OFFSET_P1];
        if ((pin_nb < 0) || (pin_nb >= MAX_NUM_PINS))
            ISOException.throwIt(SW_INCORRECT_P1);
        OwnerPIN pin = pins[pin_nb];
        if (pin == null)
            ISOException.throwIt(SW_INCORRECT_P1);
        if (buffer[ISO7816.OFFSET_P2] != 0x00)
            ISOException.throwIt(SW_INCORRECT_P2);
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        /*
         * Here I suppose the PIN code is small enough to enter in the buffer
         * TODO: Verify the assumption and eventually adjust code to support
         * reading PIN in multiple read()s
         */
        if (!CheckPINPolicy(buffer, ISO7816.OFFSET_CDATA, (byte) bytesLeft))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        byte triesRemaining = pin.getTriesRemaining();
        if (triesRemaining == (byte) 0x00)
            ISOException.throwIt(SW_IDENTITY_BLOCKED);
        if (!pin.check(buffer, (short) ISO7816.OFFSET_CDATA, (byte) bytesLeft)) {
            LogoutIdentity(pin_nb);
            logger.createLog(INS_VERIFY_PIN, (short)-1, (short)-1, (short)(SW_PIN_FAILED + triesRemaining - 1) );
            ISOException.throwIt((short)(SW_PIN_FAILED + triesRemaining - 1));
        }

        // Actually register that PIN has been successfully verified.
        logged_ids |= (short) (0x0001 << pin_nb);

        return (short)0;
    }

    /** 
     * This function changes a PIN code. The DATA portion contains both the old and
     * the new PIN codes. 
     * 
     * ins: 0x44
     * p1: PIN number (0x00-0x07)
     * p2: 0x00
     * data: [PIN_size(1b) | old_PIN | PIN_size(1b) | new_PIN ] 
     * return: none (throws an exception in case of wrong PIN)
     */
    private short ChangePIN(APDU apdu, byte[] buffer) {
        /*
         * Here I suppose the PIN code is small enough that 2 of them enter in
         * the buffer TODO: Verify the assumption and eventually adjust code to
         * support reading PINs in multiple read()s
         */
        byte pin_nb = buffer[ISO7816.OFFSET_P1];
        if ((pin_nb < 0) || (pin_nb >= MAX_NUM_PINS))
            ISOException.throwIt(SW_INCORRECT_P1);
        OwnerPIN pin = pins[pin_nb];
        if (pin == null)
            ISOException.throwIt(SW_INCORRECT_P1);
        if (buffer[ISO7816.OFFSET_P2] != (byte) 0x00)
            ISOException.throwIt(SW_INCORRECT_P2);
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        // At least 1 character for each PIN code
        if (bytesLeft < 4)
            ISOException.throwIt(SW_INVALID_PARAMETER);
        byte pin_size = buffer[ISO7816.OFFSET_CDATA];
        if (bytesLeft < (short) (1 + pin_size + 1))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        if (!CheckPINPolicy(buffer, (short) (ISO7816.OFFSET_CDATA + 1), pin_size))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        byte new_pin_size = buffer[(short) (ISO7816.OFFSET_CDATA + 1 + pin_size)];
        if (bytesLeft < (short) (1 + pin_size + 1 + new_pin_size))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        if (!CheckPINPolicy(buffer, (short) (ISO7816.OFFSET_CDATA + 1 + pin_size + 1), new_pin_size))
            ISOException.throwIt(SW_INVALID_PARAMETER);

        byte triesRemaining = pin.getTriesRemaining();
        if (triesRemaining == (byte) 0x00)
            ISOException.throwIt(SW_IDENTITY_BLOCKED);
        if (!pin.check(buffer, (short) (ISO7816.OFFSET_CDATA + 1), pin_size)) {
            LogoutIdentity(pin_nb);
            logger.createLog(INS_CHANGE_PIN, (short)-1, (short)-1, (short)(SW_PIN_FAILED + triesRemaining - 1) );
            ISOException.throwIt((short)(SW_PIN_FAILED + triesRemaining - 1));
        }

        pin.update(buffer, (short) (ISO7816.OFFSET_CDATA + 1 + pin_size + 1), new_pin_size);
        // JC specifies this resets the validated flag. So we do.
        logged_ids &= (short) ((short) 0xFFFF ^ (0x01 << pin_nb));

        return (short)0;
    }

    /**
     * This function unblocks a PIN number using the unblock code specified in the
     * DATA portion. The P3 byte specifies the unblock code length. 
     * 
     * ins: 0x46
     * p1: PIN number (0x00-0x07)
     * p2: 0x00
     * data: [PUK] 
     * return: none (throws an exception in case of wrong PUK)
     */
    private short UnblockPIN(APDU apdu, byte[] buffer) {
        byte pin_nb = buffer[ISO7816.OFFSET_P1];
        if ((pin_nb < 0) || (pin_nb >= MAX_NUM_PINS))
            ISOException.throwIt(SW_INCORRECT_P1);
        OwnerPIN pin = pins[pin_nb];
        OwnerPIN ublk_pin = ublk_pins[pin_nb];
        if (pin == null)
            ISOException.throwIt(SW_INCORRECT_P1);
        if (ublk_pin == null)
            ISOException.throwIt(SW_INTERNAL_ERROR);
        // If the PIN is not blocked, the call is inconsistent
        if (pin.getTriesRemaining() != 0)
            ISOException.throwIt(SW_OPERATION_NOT_ALLOWED);
        if (buffer[ISO7816.OFFSET_P2] != 0x00)
            ISOException.throwIt(SW_INCORRECT_P2);
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        /*
         * Here I suppose the PIN code is small enough to fit into the buffer
         * TODO: Verify the assumption and eventually adjust code to support
         * reading PIN in multiple read()s
         */
        if (!CheckPINPolicy(buffer, ISO7816.OFFSET_CDATA, (byte) bytesLeft))
            ISOException.throwIt(SW_INVALID_PARAMETER);
        byte triesRemaining = ublk_pin.getTriesRemaining();
        if (triesRemaining == (byte) 0x00)
            ISOException.throwIt(SW_IDENTITY_BLOCKED);
        if (!ublk_pin.check(buffer, ISO7816.OFFSET_CDATA, (byte) bytesLeft)){
            logger.createLog(INS_UNBLOCK_PIN, (short)-1, (short)-1, (short)(SW_PIN_FAILED + triesRemaining - 1) );
            ISOException.throwIt((short)(SW_PIN_FAILED + triesRemaining - 1));
        }

        pin.resetAndUnblock();

        return (short)0;
    }

    private short LogOutAll() {
        logged_ids = (short) 0x0000; // Nobody is logged in
        byte i;
        for (i = (byte) 0; i < MAX_NUM_PINS; i++)
            if (pins[i] != null)
                pins[i].reset();

        return (short)0;
    }

    /**
     * This function returns a 2 byte bit mask of the available PINs that are currently in
     * use. Each set bit corresponds to an active PIN.
     * 
     *  ins: 0x48
     *  p1: 0x00
     *  p2: 0x00
     *  data: none
     *  return: [RFU(1b) | PIN_mask(1b)]
     */
    private short ListPINs(APDU apdu, byte[] buffer) {
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);

        // Checking P1 & P2
        if (buffer[ISO7816.OFFSET_P1] != (byte) 0x00)
            ISOException.throwIt(SW_INCORRECT_P1);
        if (buffer[ISO7816.OFFSET_P2] != (byte) 0x00)
            ISOException.throwIt(SW_INCORRECT_P2);
        byte expectedBytes = buffer[ISO7816.OFFSET_LC];
        if (expectedBytes != (short) 2)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        // Build the PIN bit mask
        short mask = (short) 0x00;
        short b;
        for (b = (short) 0; b < MAX_NUM_PINS; b++)
            if (pins[b] != null)
                mask |= (short) (((short) 0x01) << b);
        // Fill the buffer
        Util.setShort(buffer, (short) 0, mask);
        // Send response
        return (short)2;
    }

    /**
     * This function retrieves general information about the Applet running on the smart
     * card, and useful information about the status of current session such as:
     *      - applet version (4b)
     *  
     *  ins: 0x3C
     *  p1: 0x00 
     *  p2: 0x00 
     *  data: none
     *  return: [versions(4b) | PIN0-PUK0-PIN1-PUK1 tries (4b) | needs2FA (1b) | is_seeded(1b) | setupDone(1b) | needs_secure_channel(1b)]
     */
    private short GetStatus(APDU apdu, byte[] buffer) {
        // check that PIN[0] has been entered previously
        //if (!pins[0].isValidated())
        // ISOException.throwIt(SW_UNAUTHORIZED);

        if (buffer[ISO7816.OFFSET_P1] != (byte) 0x00)
            ISOException.throwIt(SW_INCORRECT_P1);
        if (buffer[ISO7816.OFFSET_P2] != (byte) 0x00)
            ISOException.throwIt(SW_INCORRECT_P2);

        short pos = (short) 0;
        buffer[pos++] = PROTOCOL_MAJOR_VERSION; // Major Card Edge Protocol version n.
        buffer[pos++] = PROTOCOL_MINOR_VERSION; // Minor Card Edge Protocol version n.
        buffer[pos++] = APPLET_MAJOR_VERSION; // Major Applet version n.
        buffer[pos++] = APPLET_MINOR_VERSION; // Minor Applet version n.
        // PIN/PUK remaining tries available
        if (setupDone){
            buffer[pos++] = pins[0].getTriesRemaining();
            buffer[pos++] = ublk_pins[0].getTriesRemaining();
            buffer[pos++] = pins[1].getTriesRemaining();
            buffer[pos++] = ublk_pins[1].getTriesRemaining();
        } else {
            buffer[pos++] = (byte) 0;
            buffer[pos++] = (byte) 0;
            buffer[pos++] = (byte) 0;
            buffer[pos++] = (byte) 0;
        }
        if (false) // needs_2FA => maintained for intercompatibility with Satochip but not (currently) used
            buffer[pos++] = (byte)0x01;
        else
            buffer[pos++] = (byte)0x00;
        if (true) // bip32_seeded => maintained for intercompatibility with Satochip but not used
            buffer[pos++] = (byte)0x01;
        else
            buffer[pos++] = (byte)0x00;
        if (setupDone)
            buffer[pos++] = (byte)0x01;
        else
            buffer[pos++] = (byte)0x00;
        if (needs_secure_channel)
            buffer[pos++] = (byte)0x01;
        else
            buffer[pos++] = (byte)0x00;

        return pos;
    }
    
    /**
     * This function allows to define or recover a short description of the card.
     * 
     *  ins: 0x3D
     *  p1: 0x00 
     *  p2: operation (0x00 to set label, 0x01 to get label)
     *  data: [label_size(1b) | label ] if p2==0x00 else (none)
     *  return: [label_size(1b) | label ] if p2==0x01 else (none)
     */
    private short card_label(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        byte op = buffer[ISO7816.OFFSET_P2];
        switch (op) {
            case 0x00: // set label
                short bytes_left = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
                short buffer_offset = ISO7816.OFFSET_CDATA;
                if (bytes_left>0){
                    short label_size= Util.makeShort((byte) 0x00, buffer[buffer_offset]);
                    if (label_size>bytes_left)
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    if (label_size>MAX_CARD_LABEL_SIZE)
                        ISOException.throwIt(SW_INVALID_PARAMETER);
                    card_label_size= buffer[buffer_offset];
                    bytes_left--;
                    buffer_offset++;
                    Util.arrayCopyNonAtomic(buffer, buffer_offset, card_label, (short)0, label_size);
                }
                else if (bytes_left==0){//reset label
                    card_label_size= (byte)0x00;
                }
                return (short)0;
                
            case 0x01: // get label
                buffer[(short)0]=card_label_size;
                Util.arrayCopyNonAtomic(card_label, (short)0, buffer, (short)1, card_label_size);
                return (short)(card_label_size+1);
                
            default:
                ISOException.throwIt(SW_INCORRECT_P2);
                
        }//end switch()
        
        return (short)0;
    }
    
    /**
     * DEPRECATED - use exportAuthentikey() instead
     * This function returns the authentikey public key (uniquely derived from the Bip32 seed).
     * The function returns the x-coordinate of the authentikey, self-signed.
     * The authentikey full public key can be recovered from the signature.
     * 
     *  ins: 0x73
     *  p1: 0x00 
     *  p2: 0x00 
     *  data: none
     *  return: [coordx_size(2b) | coordx | sig_size(2b) | sig]
     */
    private short getBIP32AuthentiKey(APDU apdu, byte[] buffer){
        return getAuthentikey(apdu, buffer);
    }
    
    /**
     * This function returns the authentikey public key.
     * The function returns the x-coordinate of the authentikey, self-signed.
     * The authentikey full public key can be recovered from the signature.
     * 
     * Compared to getBIP32AuthentiKey(), this method returns the Authentikey even if the card is not seeded.
     * For SeedKeeper encrypted seed import, we use the authentikey as a Trusted Pubkey for the ECDH key exchange, 
     * thus the authentikey must be available before the Satochip is seeded. 
     * Before a seed is available, the authentiey is generated oncard randomly in the constructor
     * 
     *  ins: 0xAD
     *  p1: 0x00 
     *  p2: 0x00 
     *  data: none
     *  return: [coordx_size(2b) | coordx | sig_size(2b) | sig]
     */
    private short getAuthentikey(APDU apdu, byte[] buffer){
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        // get the partial authentikey public key...
        authentikey_public.getW(buffer, (short)1);
        Util.setShort(buffer, (short)0, BIP32_KEY_SIZE);
        // self signed public key
        sigECDSA.init(authentikey_private, Signature.MODE_SIGN);
        short sign_size= sigECDSA.sign(buffer, (short)0, (short)(BIP32_KEY_SIZE+2), buffer, (short)(BIP32_KEY_SIZE+4));
        Util.setShort(buffer, (short)(BIP32_KEY_SIZE+2), sign_size);
        
        // return x-coordinate of public key+signature
        // the client can recover full public-key from the signature or
        // by guessing the compression value () and verifying the signature... 
        // buffer= [coordx_size(2) | coordx | sigsize(2) | sig]
        return (short)(BIP32_KEY_SIZE+sign_size+4);
    }
    
    /**
     * This function allows to initiate a Secure Channel
     *  
     *  ins: 0x81
     *  p1: 0x00
     *  p2: 0x00
     *  data: [client-pubkey(65b)]
     *  return: [coordx_size(2b) | authentikey-coordx | sig_size(2b) | self-sig | sig2_size(optional) | authentikey-sig(optional)]
     */
    private short InitiateSecureChannel(APDU apdu, byte[] buffer){

        // get client pubkey
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        if (bytesLeft < (short)65)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        if (buffer[ISO7816.OFFSET_CDATA] != (byte)0x04)
            ISOException.throwIt(SW_INVALID_PARAMETER);

        // generate a new ephemeral key
        sc_ephemeralkey.clearKey(); //todo: simply generate new random S param instead?
        Secp256k1.setCommonCurveParameters(sc_ephemeralkey);// keep public params!
        randomData.generateData(recvBuffer, (short)0, BIP32_KEY_SIZE);
        sc_ephemeralkey.setS(recvBuffer, (short)0, BIP32_KEY_SIZE); //random value first

        // compute the shared secret...
        keyAgreement.init(sc_ephemeralkey);        
        keyAgreement.generateSecret(buffer, ISO7816.OFFSET_CDATA, (short) 65, recvBuffer, (short)0); //pubkey in uncompressed form
        // derive sc_sessionkey & sc_mackey
        HmacSha160.computeHmacSha160(recvBuffer, (short)1, (short)32, CST_SC, (short)6, (short)6, recvBuffer, (short)33);
        Util.arrayCopyNonAtomic(recvBuffer, (short)33, sc_buffer, OFFSET_SC_MACKEY, SIZE_SC_MACKEY);
        HmacSha160.computeHmacSha160(recvBuffer, (short)1, (short)32, CST_SC, (short)0, (short)6, recvBuffer, (short)33);
        sc_sessionkey.setKey(recvBuffer,(short)33); // AES-128: 16-bytes key!!       

        //reset IV counter
        Util.arrayFillNonAtomic(sc_buffer, OFFSET_SC_IV, SIZE_SC_IV, (byte) 0);

        // self signed ephemeral pubkey
        keyAgreement.generateSecret(Secp256k1.SECP256K1, Secp256k1.OFFSET_SECP256K1_G, (short) 65, buffer, (short)1); //pubkey in uncompressed form
        Util.setShort(buffer, (short)0, BIP32_KEY_SIZE);
        sigECDSA.init(sc_ephemeralkey, Signature.MODE_SIGN);
        short sign_size= sigECDSA.sign(buffer, (short)0, (short)(BIP32_KEY_SIZE+2), buffer, (short)(BIP32_KEY_SIZE+4));
        Util.setShort(buffer, (short)(BIP32_KEY_SIZE+2), sign_size);

        // hash signed by authentikey
        short offset= (short)(2+BIP32_KEY_SIZE+2+sign_size);
        sigECDSA.init(authentikey_private, Signature.MODE_SIGN);
        short sign2_size= sigECDSA.sign(buffer, (short)0, offset, buffer, (short)(offset+2));
        Util.setShort(buffer, offset, sign2_size);
        offset+=(short)(2+sign2_size); 

        initialized_secure_channel= true;

        // return x-coordinate of public key+signature
        // the client can recover full public-key from the signature or
        // by guessing the compression value () and verifying the signature... 
        // buffer= [coordx_size(2) | coordx | sigsize(2) | sig | sig2_size(optional) | sig2(optional)]
        return offset;
    }

    /**
     * This function allows to decrypt a secure channel message
     *  
     *  ins: 0x82
     *  
     *  p1: 0x00 (RFU)
     *  p2: 0x00 (RFU)
     *  data: [IV(16b) | data_size(2b) | encrypted_command | mac_size(2b) | mac]
     *  
     *  return: [decrypted command]
     *   
     */
    private short ProcessSecureChannel(APDU apdu, byte[] buffer){

        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        short offset = ISO7816.OFFSET_CDATA;

        if (!initialized_secure_channel){
            ISOException.throwIt(SW_SECURE_CHANNEL_UNINITIALIZED);
        }

        // check hmac
        if (bytesLeft<18)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        short sizein = Util.getShort(buffer, (short) (offset+SIZE_SC_IV));
        if (bytesLeft<(short)(SIZE_SC_IV+2+sizein+2))
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        short sizemac= Util.getShort(buffer, (short) (offset+SIZE_SC_IV+2+sizein));
        if (sizemac != (short)20)
            ISOException.throwIt(SW_SECURE_CHANNEL_WRONG_MAC);
        if (bytesLeft<(short)(SIZE_SC_IV+2+sizein+2+sizemac))
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        HmacSha160.computeHmacSha160(sc_buffer, OFFSET_SC_MACKEY, SIZE_SC_MACKEY, buffer, offset, (short)(SIZE_SC_IV+2+sizein), tmpBuffer2, (short)0);
        if ( Util.arrayCompare(tmpBuffer2, (short)0, buffer, (short)(offset+SIZE_SC_IV+2+sizein+2), (short)20) != (byte)0 )
            ISOException.throwIt(SW_SECURE_CHANNEL_WRONG_MAC);

        // process IV
        // IV received from client should be odd and strictly greater than locally saved IV
        // IV should be random (the 12 first bytes), never reused (the last 4 bytes counter) and different for send and receive
        if ((buffer[(short)(offset+SIZE_SC_IV-(short)1)] & (byte)0x01)==0x00)// should be odd
            ISOException.throwIt(SW_SECURE_CHANNEL_WRONG_IV);
        if ( !Biginteger.lessThan(sc_buffer, OFFSET_SC_IV_COUNTER, buffer, (short)(offset+SIZE_SC_IV_RANDOM), SIZE_SC_IV_COUNTER ) ) //and greater than local IV
            ISOException.throwIt(SW_SECURE_CHANNEL_WRONG_IV);
        // update local IV
        Util.arrayCopy(buffer, (short)(offset+SIZE_SC_IV_RANDOM), sc_buffer, OFFSET_SC_IV_COUNTER, SIZE_SC_IV_COUNTER);
        Biginteger.add1_carry(sc_buffer, OFFSET_SC_IV_COUNTER, SIZE_SC_IV_COUNTER);
        randomData.generateData(sc_buffer, OFFSET_SC_IV_RANDOM, SIZE_SC_IV_RANDOM);
        sc_aes128_cbc.init(sc_sessionkey, Cipher.MODE_DECRYPT, buffer, offset, SIZE_SC_IV);
        offset+=SIZE_SC_IV;
        bytesLeft-=SIZE_SC_IV;

        //decrypt command
        offset+=2;
        bytesLeft-=2;
        if (bytesLeft<sizein)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        short sizeout=sc_aes128_cbc.doFinal(buffer, offset, sizein, buffer, (short) (0));
        return sizeout;
    }
    
    
    /*********************************************
     *      Methods for PKI personalization      *
     *********************************************/
    
    /**
     * This function is used to self-sign the CSR of the device
     *  
     *  ins: 0x94
     *  p1: 0x00  
     *  p2: 0x00 
     *  data: [hash(32b)]
     *  return: [signature]
     */
    private short sign_PKI_CSR(APDU apdu, byte[] buffer) {
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        if (personalizationDone)
            ISOException.throwIt(SW_PKI_ALREADY_LOCKED);
        
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        if (bytesLeft < (short)32)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        
        sigECDSA.init(authentikey_private, Signature.MODE_SIGN);
        short sign_size= sigECDSA.signPreComputedHash(buffer, ISO7816.OFFSET_CDATA, MessageDigest.LENGTH_SHA_256, buffer, (short)0);
        return sign_size;
    }
    
    /**
     * This function export the ECDSA secp256k1 public key that corresponds to the private key
     *  
     *  ins: 
     *  p1: 0x00
     *  p2: 0x00 
     *  data: [none]
     *  return: [ pubkey (65b) ]
     */
    private short export_PKI_pubkey(APDU apdu, byte[] buffer) {
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        authentikey_public.getW(buffer, (short)0); 
        return (short)65;
    }
    
    /**
     * This function imports the device certificate
     *  
     *  ins: 
     *  p1: 0x00
     *  p2: Init-Update 
     *  data(init): [ full_size(2b) ]
     *  data(update): [chunk_offset(2b) | chunk_size(2b) | chunk_data ]
     *  return: [none]
     */
    private short import_PKI_certificate(APDU apdu, byte[] buffer) {
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        if (personalizationDone)
            ISOException.throwIt(SW_PKI_ALREADY_LOCKED);
        
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        short buffer_offset = (short) (ISO7816.OFFSET_CDATA);
        
        byte op = buffer[ISO7816.OFFSET_P2];
        switch(op){
            case OP_INIT:
                if (bytesLeft < (short)2)
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                
                short new_certificate_size=Util.getShort(buffer, buffer_offset);
                if (new_certificate_size < 0)
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                if (authentikey_certificate==null){
                    // create array
                    authentikey_certificate= new byte[new_certificate_size];
                    authentikey_certificate_size=new_certificate_size;
                }else{
                    if (new_certificate_size>authentikey_certificate.length)
                        ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                    authentikey_certificate_size=new_certificate_size;
                }
                break;
                
            case OP_PROCESS: 
                if (bytesLeft < (short)4)
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                short chunk_offset= Util.getShort(buffer, buffer_offset);
                buffer_offset+=2;
                short chunk_size= Util.getShort(buffer, buffer_offset);
                buffer_offset+=2;
                bytesLeft-=4;
                if (bytesLeft < chunk_size)
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                if ((chunk_offset<0) || (chunk_offset>=authentikey_certificate_size))
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                if (((short)(chunk_offset+chunk_size))>authentikey_certificate_size)
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                
                Util.arrayCopyNonAtomic(buffer, buffer_offset, authentikey_certificate, chunk_offset, chunk_size);
                break;
                
            default:
                ISOException.throwIt(SW_INCORRECT_P2);
        }
        return (short)0;
    }
    
    /**
     * This function exports the device certificate
     *  
     *  ins: 
     *  p1: 0x00  
     *  p2: Init-Update 
     *  data(init): [ none ]
     *  return(init): [ full_size(2b) ]
     *  data(update): [ chunk_offset(2b) | chunk_size(2b) ]
     *  return(update): [ chunk_data ] 
     */
    private short export_PKI_certificate(APDU apdu, byte[] buffer) {
        // check that PIN[0] has been entered previously
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        byte op = buffer[ISO7816.OFFSET_P2];
        switch(op){
            case OP_INIT:
                Util.setShort(buffer, (short)0, authentikey_certificate_size);
                return (short)2; 
                
            case OP_PROCESS: 
                short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
                if (bytesLeft < (short)4)
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                
                short buffer_offset = (short) (ISO7816.OFFSET_CDATA);
                short chunk_offset= Util.getShort(buffer, buffer_offset);
                buffer_offset+=2;
                short chunk_size= Util.getShort(buffer, buffer_offset);
                
                if ((chunk_offset<0) || (chunk_offset>=authentikey_certificate_size))
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                if (((short)(chunk_offset+chunk_size))>authentikey_certificate_size)
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                Util.arrayCopyNonAtomic(authentikey_certificate, chunk_offset, buffer, (short)0, chunk_size);
                return chunk_size; 
                
            default:
                ISOException.throwIt(SW_INCORRECT_P2);
                return (short)0; 
        }
    }
    
    /**
     * This function locks the PKI config.
     * Once it is locked, it is not possible to modify private key, certificate or allowed_card_AID.
     *  
     *  ins: 
     *  p1: 0x00 
     *  p2: 0x00 
     *  data: [none]
     *  return: [none]
     */
    private short lock_PKI(APDU apdu, byte[] buffer) {
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        personalizationDone=true;
        return (short)0;
    }
    
    /**
     * This function performs a challenge-response to verify the authenticity of the device.
     * The challenge is made of three parts: 
     *          - a constant header
     *          - a 32-byte challenge provided by the requester
     *          - a 32-byte random nonce generated by the device
     * The response is the signature over this challenge. 
     * This signature can be verified with the certificate stored in the device.
     * 
     *  ins: 
     *  p1: 0x00 
     *  p2: 0x00 
     *  data: [challenge1(32b)]
     *  return: [challenge2(32b) | sig_size(2b) | sig]
     */
    private short challenge_response_pki(APDU apdu, byte[] buffer) {
        // todo: require PIN?
        if (!pins[0].isValidated())
            ISOException.throwIt(SW_UNAUTHORIZED);
        
        short bytesLeft = Util.makeShort((byte) 0x00, buffer[ISO7816.OFFSET_LC]);
        if (bytesLeft < (short)32)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        
        //copy all data into array
        short offset=(short)0;
        Util.arrayCopyNonAtomic(PKI_CHALLENGE_MSG, (short)0, recvBuffer, offset, (short)PKI_CHALLENGE_MSG.length);
        offset+=PKI_CHALLENGE_MSG.length;
        randomData.generateData(recvBuffer, offset, (short)32);
        offset+=(short)32;
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, recvBuffer, offset, (short)32);
        offset+=(short)32;
         
        //sign challenge
        sigECDSA.init(authentikey_private, Signature.MODE_SIGN);
        short sign_size= sigECDSA.sign(recvBuffer, (short)0, offset, buffer, (short)34);
        Util.setShort(buffer, (short)32, sign_size);
        Util.arrayCopyNonAtomic(recvBuffer, (short)PKI_CHALLENGE_MSG.length, buffer, (short)0, (short)32);
        
        // verify response
        sigECDSA.init(authentikey_public, Signature.MODE_VERIFY);
        boolean is_valid= sigECDSA.verify(recvBuffer, (short)0, offset, buffer, (short)(34), sign_size);
        if (!is_valid)
            ISOException.throwIt(SW_SIGNATURE_INVALID);
        
        return (short)(32+2+sign_size);
    }

} // end of class JAVA_APPLET

