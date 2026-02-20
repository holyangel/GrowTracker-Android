package me.anon.lib.manager

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.widget.Toast
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Types
import me.anon.grow.MainApplication
import me.anon.lib.helper.EncryptionHelper
import me.anon.lib.helper.MoshiHelper
import me.anon.model.FeedingSchedule
import java.io.File
import java.util.*

class ScheduleManager private constructor()
{
	public val schedules: ArrayList<FeedingSchedule> = arrayListOf()
	private lateinit var context: Context

	fun initialise(context: Context)
	{
		this.context = context.applicationContext
		FILES_DIR = this.context.getExternalFilesDir(null)!!.absolutePath

		load()
	}

	public val fileExt: String
		get() {
			if (MainApplication.isEncrypted()) return "dat"
			else return "json"
		}

	public fun indexOf(schedule: FeedingSchedule) = schedules.indexOfFirst { it.id == schedule.id }

	public fun upsert(schedule: FeedingSchedule)
	{
		var index = indexOf(schedule)

		if (index < 0)
		{
			insert(schedule)
		}
		else
		{
			this.schedules[index] = schedule
		}

		save()
	}

	fun load()
	{
		if (FileManager.getInstance().fileExists("$FILES_DIR/schedules.${fileExt}"))
		{
			val scheduleData = if (MainApplication.isEncrypted())
			{
				if (TextUtils.isEmpty(MainApplication.getKey()))
				{
					return
				}

				EncryptionHelper.decrypt(MainApplication.getKey(), FileManager.getInstance().readFile("$FILES_DIR/schedules.${fileExt}"))
			}
			else
			{
				FileManager.getInstance().readFileAsString("$FILES_DIR/schedules.${fileExt}")
			}

			try
			{
				if (!TextUtils.isEmpty(scheduleData))
				{
					schedules.clear()
					schedules.addAll(MoshiHelper.parse<Any>(scheduleData, Types.newParameterizedType(ArrayList::class.java, FeedingSchedule::class.java)) as ArrayList<FeedingSchedule>)
				}
			}
			catch (e: JsonDataException)
			{
				e.printStackTrace()
			}

		}
	}

	fun save()
	{
		synchronized(schedules) {
			val targetPath = "$FILES_DIR/schedules.$fileExt"
			val tempPath = "$targetPath.tmp"

			try
			{
				val json = MoshiHelper.toJson(schedules, Types.newParameterizedType(ArrayList::class.java, FeedingSchedule::class.java))
				if (json.isNullOrEmpty()) return

				if (MainApplication.isEncrypted())
				{
					if (TextUtils.isEmpty(MainApplication.getKey()))
					{
						return
					}

					FileManager.getInstance().writeFile(tempPath, EncryptionHelper.encrypt(MainApplication.getKey(), json))
				}
				else
				{
					FileManager.getInstance().writeFile(tempPath, json)
				}

				val tempFile = File(tempPath)
				if (!tempFile.exists() || tempFile.length() == 0L)
				{
					tempFile.delete()
					return
				}

				// Back up current good file, then rename temp to target
				val target = File(targetPath)
				if (target.exists() && target.length() > 0)
				{
					FileManager.getInstance().copyFile(targetPath, "$targetPath.bak")
				}
				tempFile.renameTo(target)
			}
			catch (e: Exception)
			{
				e.printStackTrace()

				if (File(targetPath).length() == 0L || !File(targetPath).exists())
				{
					Toast.makeText(context, "There was a fatal problem saving the schedule data, please backup this data", Toast.LENGTH_LONG).show()
					val sendData = MoshiHelper.toJson(schedules, Types.newParameterizedType(ArrayList::class.java, FeedingSchedule::class.java))
					val share = Intent(Intent.ACTION_SEND)
					share.type = "text/plain"
					share.putExtra(Intent.EXTRA_TEXT, "== WARNING : PLEASE BACK UP THIS DATA == \r\n\r\n $sendData")
					context.startActivity(share)
				}

				return
			}
		}
	}

	fun insert(schedule: FeedingSchedule)
	{
		schedules.add(schedule)
		save()
	}

	companion object
	{
		@JvmField public val instance = ScheduleManager()
		@JvmField public var FILES_DIR: String? = null
	}
}
