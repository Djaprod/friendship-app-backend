package com.artii.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Session tokens are encrypted with a non-exportable Android Keystore key, never backed up.
class SessionVault(context:Context,storageName:String="artii-session"){
 private val prefs=context.getSharedPreferences(storageName,Context.MODE_PRIVATE)
 private fun key():SecretKey{
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  return (store.getKey("artii-session-v2",null) as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("artii-session-v2",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 }
 fun read():String=runCatching{
  val raw=prefs.getString("encrypted",null)?:return ""
  val parts=raw.split(':');val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
  String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8)
 }.getOrElse{clear();""}
 fun save(token:String){val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val value=Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(token.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP);check(prefs.edit().putString("encrypted",value).commit()){"Could not save your session. Please try again."}}
 fun clear(){prefs.edit().clear().apply()}
}
