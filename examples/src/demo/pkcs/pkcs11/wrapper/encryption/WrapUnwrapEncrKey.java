// Copyright (c) 2002 Graz University of Technology. All rights reserved.
// 
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
// 
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
// 
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
// 
// 3. The end-user documentation included with the redistribution, if any, must
//    include the following acknowledgment:
// 
//    "This product includes software developed by IAIK of Graz University of
//     Technology."
// 
//    Alternately, this acknowledgment may appear in the software itself, if and
//    wherever such third-party acknowledgments normally appear.
// 
// 4. The names "Graz University of Technology" and "IAIK of Graz University of
//    Technology" must not be used to endorse or promote products derived from this
//    software without prior written permission.
// 
// 5. Products derived from this software may not be called "IAIK PKCS Wrapper",
//    nor may "IAIK" appear in their name, without prior written permission of
//    Graz University of Technology.
// 
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
// PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE LICENSOR BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
// OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
// PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
// OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
// ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package demo.pkcs.pkcs11.wrapper.encryption;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Module;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Slot;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.objects.AESSecretKey;
import iaik.pkcs.pkcs11.objects.Attribute;
import iaik.pkcs.pkcs11.objects.CharArrayAttribute;
import iaik.pkcs.pkcs11.objects.DES3SecretKey;
import iaik.pkcs.pkcs11.objects.DHPrivateKey;
import iaik.pkcs.pkcs11.objects.GenericTemplate;
import iaik.pkcs.pkcs11.objects.Key;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import iaik.pkcs.pkcs11.objects.PublicKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.SecretKey;
import iaik.pkcs.pkcs11.parameters.InitializationVectorParameters;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;

import demo.pkcs.pkcs11.wrapper.util.CreateKeysAndCertificate;
import demo.pkcs.pkcs11.wrapper.util.Util;

/**
 * This demo program uses a PKCS#11 module to wrap and unwrap a secret key. The key to be wrapped
 * must be extractable otherwise it can't be wrapped.
 */
public class WrapUnwrapEncrKey {

  static PrintWriter output_;
  static BufferedReader input_;

  static {
    try {
      // output_ = new PrintWriter(new FileWriter("Encrypt_output.txt"), true);
      output_ = new PrintWriter(System.out, true);
      input_ = new BufferedReader(new InputStreamReader(System.in));
    } catch (Throwable thr) {
      thr.printStackTrace();
      output_ = new PrintWriter(System.out, true);
      input_ = new BufferedReader(new InputStreamReader(System.in));
    }
  }

  /**
   * Usage: WrapUnwrap PKCS#11-module file-to-be-encrypted [slot-id] [pin]
   */
  public static void main(String[] args) throws TokenException, IOException {
    if (args.length < 2) {
      printUsage();
      throw new IOException("Missing argument!");
    }

    Module pkcs11Module = Module.getInstance(args[0]);
    pkcs11Module.initialize(null);

    Token token;
    if (2 < args.length)
      token = Util.selectToken(pkcs11Module, output_, input_, args[2]);
    else
      token = Util.selectToken(pkcs11Module, output_, input_);
    if (token == null) {
      output_.println("We have no token to proceed. Finished.");
      output_.flush();
      throw new TokenException("No token found!");
    }

    Session session;
    if (3 < args.length)
      session = Util.openAuthorizedSession(token,
          Token.SessionReadWriteBehavior.RW_SESSION, output_, input_, args[3]);
    else
      session = Util.openAuthorizedSession(token,
          Token.SessionReadWriteBehavior.RW_SESSION, output_, input_, null);

    output_
        .println("################################################################################");
    output_.println("generate secret encryption/decryption key");
    Mechanism keyMechanism = Mechanism.get(PKCS11Constants.CKM_AES_KEY_GEN);
    AESSecretKey secretEncryptionKeyTemplate = new AESSecretKey();
    secretEncryptionKeyTemplate.getValueLen().setLongValue(new Long(16));
    secretEncryptionKeyTemplate.getEncrypt().setBooleanValue(Boolean.TRUE);
    secretEncryptionKeyTemplate.getDecrypt().setBooleanValue(Boolean.TRUE);
    secretEncryptionKeyTemplate.getPrivate().setBooleanValue(Boolean.TRUE);
    secretEncryptionKeyTemplate.getSensitive().setBooleanValue(Boolean.TRUE);
    secretEncryptionKeyTemplate.getExtractable().setBooleanValue(Boolean.TRUE);
    secretEncryptionKeyTemplate.getWrap().setBooleanValue(Boolean.TRUE);

    AESSecretKey encryptionKey = (AESSecretKey) session.generateKey(keyMechanism,
        secretEncryptionKeyTemplate);

    output_
        .println("################################################################################");

    output_
        .println("################################################################################");
    output_.println("encrypting data from file: " + args[1]);

    InputStream dataInputStream = new FileInputStream(args[1]);

    byte[] dataBuffer = new byte[1024];
    int bytesRead;
    ByteArrayOutputStream streamBuffer = new ByteArrayOutputStream();

    // feed in all data from the input stream
    while ((bytesRead = dataInputStream.read(dataBuffer)) >= 0) {
      streamBuffer.write(dataBuffer, 0, bytesRead);
    }
    Arrays.fill(dataBuffer, (byte) 0); // ensure that no data is left in the
                                       // memory
    streamBuffer.flush();
    streamBuffer.close();
    byte[] rawData = streamBuffer.toByteArray();

    // be sure that your token can process the specified mechanism
    Mechanism encryptionMechanism = Mechanism.get(PKCS11Constants.CKM_AES_CBC_PAD);
    byte[] encryptInitializationVector = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    InitializationVectorParameters encryptInitializationVectorParameters = new InitializationVectorParameters(
        encryptInitializationVector);
    encryptionMechanism.setParameters(encryptInitializationVectorParameters);

    // initialize for encryption
    session.encryptInit(encryptionMechanism, encryptionKey);

    byte[] encryptedData = session.encrypt(rawData);

    output_
        .println("################################################################################");

    output_.println("generate secret wrapping key");

    AESSecretKey wrappingKey = (AESSecretKey) session.generateKey(keyMechanism,
        secretEncryptionKeyTemplate);

    output_.println("wrapping key");

    byte[] wrappedKey = session.wrapKey(encryptionMechanism, wrappingKey, encryptionKey);
    AESSecretKey keyTemplate = new AESSecretKey();
    keyTemplate.getDecrypt().setBooleanValue(Boolean.TRUE);

    output_.println("unwrapping key");

    AESSecretKey unwrappedKey = (AESSecretKey) session.unwrapKey(encryptionMechanism,
        wrappingKey, wrappedKey, keyTemplate);

    output_
        .println("################################################################################");
    output_.println("trying to decrypt");

    Mechanism decryptionMechanism = Mechanism.get(PKCS11Constants.CKM_AES_CBC_PAD);
    byte[] decryptInitializationVector = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    InitializationVectorParameters decryptInitializationVectorParameters = new InitializationVectorParameters(
        decryptInitializationVector);
    decryptionMechanism.setParameters(decryptInitializationVectorParameters);

    // initialize for decryption
    session.decryptInit(decryptionMechanism, unwrappedKey);

    byte[] decryptedData = session.decrypt(encryptedData);

    // compare initial data and decrypted data
    boolean equal = false;
    if (rawData.length != decryptedData.length) {
      equal = false;
    } else {
      equal = true;
      for (int i = 0; i < rawData.length; i++) {
        if (rawData[i] != decryptedData[i]) {
          equal = false;
          break;
        }
      }
    }

    output_.println((equal) ? "successful" : "ERROR");

    output_
        .println("################################################################################");

    session.closeSession();
    pkcs11Module.finalize(null);
  }

  public static void printUsage() {
    output_
        .println("Usage: WrapUnwrap <PKCS#11 module> <file to be encrypted> [<slot-id>] [<pin>]");
    output_.println(" e.g.: WrapUnwrap pk2priv.dll data.dat 0 1234");
    output_.println("The given DLL must be in the search path of the system.");
  }

}
