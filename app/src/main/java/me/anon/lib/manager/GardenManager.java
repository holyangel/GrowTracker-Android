package me.anon.lib.manager;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Types;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import me.anon.grow.MainApplication;
import me.anon.grow.R;
import me.anon.lib.helper.EncryptionHelper;
import me.anon.lib.helper.MoshiHelper;
import me.anon.model.Garden;

public class GardenManager
{
	private static final GardenManager instance = new GardenManager();

	public static GardenManager getInstance()
	{
		return instance;
	}

	public static String FILES_DIR;

	private final ArrayList<Garden> mGardens = new ArrayList<>();
	private Context context;

	private GardenManager(){}

	public ArrayList<Garden> getGardens()
	{
		return mGardens;
	}

	public void initialise(Context context)
	{
		this.context = context.getApplicationContext();
		FILES_DIR = this.context.getExternalFilesDir(null).getAbsolutePath();

		load();
	}

	public String getFileExt()
	{
		if (MainApplication.isEncrypted()) return "dat";
		else return "json";
	}

	public void load()
	{
		if (FileManager.getInstance().fileExists(FILES_DIR + "/gardens." + getFileExt()))
		{
			String gardenData;

			if (MainApplication.isEncrypted())
			{
				if (TextUtils.isEmpty(MainApplication.getKey()))
				{
					return;
				}

				gardenData = EncryptionHelper.decrypt(MainApplication.getKey(), FileManager.getInstance().readFile(FILES_DIR + "/gardens." + getFileExt()));
			}
			else
			{
				gardenData = FileManager.getInstance().readFileAsString(FILES_DIR + "/gardens." + getFileExt());
			}

			try
			{
				if (!TextUtils.isEmpty(gardenData))
				{
					mGardens.clear();
					mGardens.addAll((Collection<? extends Garden>)MoshiHelper.parse(gardenData, Types.newParameterizedType(ArrayList.class, Garden.class)));
				}
			}
			catch (JsonDataException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void save()
	{
		synchronized (mGardens)
		{
			String targetPath = FILES_DIR + "/gardens." + getFileExt();
			String tempPath = targetPath + ".tmp";

			try
			{
				String json = MoshiHelper.toJson(mGardens, Types.newParameterizedType(ArrayList.class, Garden.class));
				if (json == null || json.isEmpty()) return;

				if (MainApplication.isEncrypted())
				{
					if (TextUtils.isEmpty(MainApplication.getKey()))
					{
						return;
					}

					FileManager.getInstance().writeFile(tempPath, EncryptionHelper.encrypt(MainApplication.getKey(), json));
				}
				else
				{
					FileManager.getInstance().writeFile(tempPath, json);
				}

				File tempFile = new File(tempPath);
				if (!tempFile.exists() || tempFile.length() == 0)
				{
					tempFile.delete();
					return;
				}

				// Back up current good file, then rename temp to target
				File target = new File(targetPath);
				if (target.exists() && target.length() > 0)
				{
					FileManager.getInstance().copyFile(targetPath, targetPath + ".bak");
				}
				tempFile.renameTo(target);
			}
			catch (Exception e)
			{
				e.printStackTrace();

				if (new File(targetPath).length() == 0 || !new File(targetPath).exists())
				{
					Toast.makeText(context, R.string.fatal_error, Toast.LENGTH_LONG).show();
					String sendData = MoshiHelper.toJson(mGardens, Types.newParameterizedType(ArrayList.class, Garden.class));
					Intent share = new Intent(Intent.ACTION_SEND);
					share.setType("text/plain");
					share.putExtra(Intent.EXTRA_TEXT, "== WARNING : PLEASE BACK UP THIS DATA == \r\n\r\n " + sendData);
					context.startActivity(share);
				}
			}
		}
	}

	public void upsert(Garden garden)
	{
		int pos = -1;
		for (int index = 0, mGardensSize = mGardens.size(); index < mGardensSize; index++)
		{
			Garden mGarden = mGardens.get(index);
			if (mGarden.getId().equals(garden.getId()))
			{
				pos = index;
				break;
			}
		}

		if (pos > -1)
		{
			mGardens.set(pos, garden);
			save();
		}
		else
		{
			insert(garden);
		}
	}

	public void insert(Garden garden)
	{
		mGardens.add(garden);
		save();
	}
}
