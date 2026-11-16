package com.lightdialer.data

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.lightdialer.util.Perms
import java.util.concurrent.Executors

data class Suggestion(val name: String, val number: String)
data class ContactRow(val name: String, val number: String)

object Contacts {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Best-effort display name for a phone number (used for incoming calls). */
    fun lookupNameAsync(context: Context, number: String, cb: (String?) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val name = try { lookupName(app, number) } catch (e: Throwable) { null }
            main.post {
                try { cb(name) } catch (e: Throwable) { }
            }
        }
    }

    private fun lookupName(context: Context, number: String): String? {
        if (number.isBlank()) return null
        if (!Perms.granted(context, Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    /** All contacts with a phone number, one row per contact, alphabetically sorted. */
    fun allAsync(context: Context, cb: (List<ContactRow>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val list = try { all(app) } catch (e: Throwable) { emptyList<ContactRow>() }
            main.post {
                try { cb(list) } catch (e: Throwable) { }
            }
        }
    }

    private fun all(context: Context): List<ContactRow> {
        if (!Perms.granted(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<ContactRow>(300)
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.Contacts.SORT_KEY_PRIMARY + " ASC"
        )?.use { c ->
            val iName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iNum = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = HashSet<String>()
            while (c.moveToNext() && out.size < 1000) {
                val n = c.getString(iName) ?: continue
                val key = n.lowercase()
                if (seen.add(key)) {
                    val number = c.getString(iNum) ?: continue
                    out.add(ContactRow(n, number))
                }
            }
        }
        return out
    }

    /** Number-prefix suggestions for the dialpad (max 5). */
    fun suggestionsAsync(context: Context, prefix: String, cb: (List<Suggestion>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val list = try { suggestions(app, prefix) } catch (e: Throwable) { emptyList() }
            main.post {
                try { cb(list) } catch (e: Throwable) { }
            }
        }
    }

    private fun suggestions(context: Context, prefix: String): List<Suggestion> {
        val digits = prefix.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (digits.length < 2) return emptyList()
        if (!Perms.granted(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(digits)
        )
        val out = ArrayList<Suggestion>(5)
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.Contacts.SORT_KEY_PRIMARY + " ASC"
        )?.use { c ->
            val iName = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iNum = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = HashSet<String>()
            while (c.moveToNext() && out.size < 5) {
                val n = c.getString(iNum) ?: continue
                if (seen.add(n.replace(" ", ""))) {
                    out.add(Suggestion(c.getString(iName) ?: n, n))
                }
            }
        }
        return out
    }
}
