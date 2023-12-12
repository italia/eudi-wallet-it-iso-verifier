package it.ipzs.scan_data.utils.documents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import it.ipzs.scan_data.utils.documents.types.RequestMdl

//todo: this can be improved, it doesn't make sense to keep it separated from the RequestMdl class
class DocumentHandler {

    private val map = HashMap<String, List<DataItem>>()

    private var portraitBytes: ByteArray? = null

    val portrait: Bitmap? get() = portraitBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    fun setPortraitBytes(bytes: ByteArray){
        this.portraitBytes = bytes
    }

    fun addField(key: String, dataItems: List<DataItem>){
        map[key] = dataItems
    }

    fun getName() = map[RequestMdl.DataItems.GIVEN_NAMES.identifier]?.let {
        parseAsString(it.firstOrNull())
    }

    fun getFamilyName() = map[RequestMdl.DataItems.FAMILY_NAME.identifier]?.let {
        parseAsString(it.firstOrNull())
    }

    fun getBirthDate() = map[RequestMdl.DataItems.BIRTH_DATE.identifier]?.let {
        parseAsString(it.firstOrNull())
    }
    fun getExpiryDate() = map[RequestMdl.DataItems.EXPIRY_DATE.identifier]?.let {
        parseAsString(it.firstOrNull())
    }
    fun getIssueDate() = map[RequestMdl.DataItems.ISSUE_DATE.identifier]?.let {
        parseAsString(it.firstOrNull())
    }
    fun getIssuingCountry() = map[RequestMdl.DataItems.ISSUING_COUNTRY.identifier]?.let {
        parseAsString(it.firstOrNull())
    }
    fun getIssuingAuthority() = map[RequestMdl.DataItems.ISSUING_AUTHORITY.identifier]?.let {
        parseAsString(it.firstOrNull())
    }
    fun getDocumentNumber() = map[RequestMdl.DataItems.DOCUMENT_NUMBER.identifier]?.let {
        parseAsString(it.firstOrNull())
    }

    fun getBirthPlace() = map[RequestMdl.DataItems.BIRTH_PLACE.identifier]?.let {
        val map = (it.first() as Map)

        map.keys.find {
            parseAsString(it) == RequestMdl.DataItems.LOCALITY.identifier
        }?.let{
            parseAsString(map[it])
        }
    }

    fun getAddress() = map[RequestMdl.DataItems.ADDRESS.identifier]?.let {
        parseAsString(it.firstOrNull())
    }

    //in the type object there may be info about issue_date and expiry_date
    fun getTypes() = map[RequestMdl.DataItems.DRIVING_PRIVILEGES.identifier]?.let {

        (it.first() as Array).dataItems.mapNotNull {

            val map = (it as Map)

            map.keys.find {
                parseAsString(it) == RequestMdl.DataItems.VEHICLE_CATEGORY_CODE.identifier
            }?.let {
               parseAsString(map[it])
            }

        }
    }

    private fun parseAsString(
        dataItem: DataItem?
    ) = (dataItem as? UnicodeString)?.string
}