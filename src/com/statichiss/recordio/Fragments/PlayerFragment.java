package com.statichiss.recordio.fragments;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.statichiss.R;
import com.statichiss.recordio.ConfirmDetailsActivity;
import com.statichiss.recordio.DBContentProvider;
import com.statichiss.recordio.DatabaseHelper;
import com.statichiss.recordio.PlayerService;
import com.statichiss.recordio.RadioApplication;
import com.statichiss.recordio.RadioDetails;
import com.statichiss.recordio.RemoteControlReceiver;
import com.statichiss.recordio.recording.RecorderService;
import com.statichiss.recordio.utils.DateUtils;
import com.statichiss.recordio.utils.StringUtils;

import java.io.IOException;

/**
 * Created by chris on 20/06/2013.
 */
public class PlayerFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int FAVOURITES_LIST_ID = 0;
    private String TAG = "com.statichiss.recordio.fragments.PlayerFragment";
    private AudioManager mAudioManager;
    private ComponentName mRemoteControlReceiver;
    private SimpleCursorAdapter adapter;
    private final Uri stationContentUri;

    public PlayerFragment() {
        stationContentUri = Uri.withAppendedPath(DBContentProvider.CONTENT_URI, "stations");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.player_view, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAudioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        mRemoteControlReceiver = new ComponentName(getActivity().getPackageName(), RemoteControlReceiver.class.getName());
    }

    @Override
    public void onStart() {

        super.onStart();
        final RadioApplication radioApplication = (RadioApplication) getActivity().getApplication();

        final DatabaseHelper dbHelper = new DatabaseHelper(getActivity());

        try {
            dbHelper.openDataBase();
        } catch (IOException e) {
            Log.e(TAG, "IOException thrown when trying to access DB", e);
        }

        getLoaderManager().initLoader(FAVOURITES_LIST_ID, null, this);

//        final Cursor favouritesCursor = dbHelper.getFavourites();

        adapter = new SimpleCursorAdapter(getActivity(),
                R.layout.favourite_list_item,
//                favouritesCursor,
                null,
                new String[]{DatabaseHelper.FAVOURITES_NAME},
                new int[]{R.id.name_entry},
                0);

        ListView lstFavourites = (ListView) getActivity().findViewById(R.id.lst_favourites);

        lstFavourites.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
//                favouritesCursor.moveToPosition(pos);
                adapter.getCursor().moveToPosition(pos);
//                decideStreamOption(new RadioDetails(favouritesCursor.getString(1), null, favouritesCursor.getString(2)));
                decideStreamOption(new RadioDetails(adapter.getCursor().getString(1), null, adapter.getCursor().getString(2)));
            }
        });

        final EditText url = (EditText) getActivity().findViewById(R.id.txt_url);
        url.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (url.getText().toString().equalsIgnoreCase(getString(R.string.url_default_text_string))) {
                    url.setText("");
                }

                InputMethodManager m = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (m != null) {
                    m.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        getActivity().findViewById(R.id.main_stop_playing_btn).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                MediaPlayer mediaPlayer = radioApplication.getMediaPlayer();
                // Bit of a hack looking at status!
                if (mediaPlayer != null && mediaPlayer.isPlaying() || radioApplication.getPlayingStatus().contains("Paused")) {
                    radioApplication.setPlayingStatus("");
                    updateUI();
                    PlayerService.sendWakefulWork(getActivity().getApplicationContext(), createPlayingIntent(null, RadioApplication.StopPlaying));
                    getActivity().findViewById(R.id.main_stop_playing_btn).setEnabled(false);
                }
            }
        });

        getActivity().findViewById(R.id.main_stop_recording_btn).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (RecorderService.alreadyRecording()) {
                    radioApplication.setRecordingStatus("");
                    updateUI();
                    RecorderService.cancelRecording();
                    getActivity().findViewById(R.id.main_stop_recording_btn).setEnabled(false);
                }
            }
        });

        lstFavourites.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            public boolean onItemLongClick(AdapterView<?> adapterView, final View view, int pos, final long id) {
                adapter.getCursor().moveToPosition(pos);

                CharSequence[] favOptions = {"Edit", "Delete"};
                AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());

                builder.setTitle(adapter.getCursor().getString(1))
                        .setItems(favOptions, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogInterface, int item) {
                                switch (item) {
                                    case 0:
                                        Intent confirmDetailsIntent = new Intent(getActivity(), ConfirmDetailsActivity.class);
                                        confirmDetailsIntent.putExtra(getString(R.string.edit_favourite_id), id);
                                        startActivity(confirmDetailsIntent);
                                        break;
                                    case 1:
                                        new AlertDialog.Builder(view.getContext())
                                                .setMessage("Delete " + adapter.getCursor().getString(1))
                                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialogInterface, int i) {
                                                        Log.d(TAG, "Deleting " + adapter.getCursor().getString(1));
                                                        try {
                                                            dbHelper.openDataBase();
                                                        } catch (IOException e) {
                                                            Log.e(TAG, "Unable to open db to delete favourite", e);
                                                        }
//                                                        dbHelper.deleteFavourite(id);
                                                        getActivity().getContentResolver().delete(stationContentUri, "_id = ?", new String[]{String.valueOf(id)});
//                                                        favouritesCursor.requery();
                                                        restartLoader();
                                                    }
                                                })
                                                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialogInterface, int i) {
                                                    }
                                                })
                                                .show();
                                        break;
                                }
                            }
                        }).show();

                return false;
            }
        });

        lstFavourites.setAdapter(adapter);
        //dbHelper.close();

        if (alreadyPlaying()) {
            getActivity().findViewById(R.id.main_stop_playing_btn).setEnabled(true);
        }

        if (RecorderService.alreadyRecording()) {
            getActivity().findViewById(R.id.main_stop_recording_btn).setEnabled(true);
        }
    }

    private void restartLoader() {
        getLoaderManager().restartLoader(FAVOURITES_LIST_ID, null, this);
    }

    protected void updateUI() {

        RadioApplication radioApplication = (RadioApplication) getActivity().getApplication();
        StringBuilder sb = new StringBuilder();

        if (!StringUtils.IsNullOrEmpty(radioApplication.getPlayingStatus()) && !StringUtils.IsNullOrEmpty(radioApplication.getRecordingStatus())) {
            sb.append(radioApplication.getPlayingStatus().length() > 23 ? radioApplication.getPlayingStatus().substring(0, 20) + "..." : radioApplication.getPlayingStatus())
                    .append(" | ")
                    .append(radioApplication.getRecordingStatus().length() > 23 ? radioApplication.getRecordingStatus().substring(0, 20) + "..." : radioApplication.getRecordingStatus());
        }

        if (!StringUtils.IsNullOrEmpty(radioApplication.getPlayingStatus()) && StringUtils.IsNullOrEmpty(radioApplication.getRecordingStatus())) {
            sb.append(radioApplication.getPlayingStatus().length() > 46 ? radioApplication.getPlayingStatus().substring(0, 43) + "..." : radioApplication.getPlayingStatus());
        }

        if (StringUtils.IsNullOrEmpty(radioApplication.getPlayingStatus()) && !StringUtils.IsNullOrEmpty(radioApplication.getRecordingStatus())) {
            sb.append(radioApplication.getRecordingStatus().length() > 46 ? radioApplication.getRecordingStatus().substring(0, 43) + "..." : radioApplication.getRecordingStatus());
        }

        TextView txtStatus = (TextView) getActivity().findViewById(R.id.txt_status);
        txtStatus.setText(sb.toString());

        getActivity().findViewById(R.id.main_stop_playing_btn).setEnabled(!StringUtils.IsNullOrEmpty(radioApplication.getPlayingStatus()));
        getActivity().findViewById(R.id.main_stop_recording_btn).setEnabled(!StringUtils.IsNullOrEmpty(radioApplication.getRecordingStatus()));
    }

    private void decideStreamOption(RadioDetails radioDetails) {

        CharSequence[] goOptions = {"Play", "Record", "Both"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final RadioDetails finalRadioDetails = radioDetails;

        builder.setTitle("Play or Record?")
                .setItems(goOptions, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int item) {
                        switch (item) {
                            case 0:
                                play(finalRadioDetails);
                                break;
                            case 1:
                                record(finalRadioDetails);
                                break;
                            case 2:
                                play(finalRadioDetails);
                                record(finalRadioDetails);
                                break;
                            default:
                                Log.e(TAG, "Unexpected option returned from dialog, option #" + item);
                                break;
                        }
                    }
                });
        builder.create().show();
    }

    protected Intent createRecordingIntent(RadioDetails radioDetails) {
        Intent intent = new Intent("com.statichiss.recordio.recording.RecorderService");
        if (radioDetails != null) {
            intent.putExtra(getString(R.string.radio_details_key), radioDetails);
        }
        return intent;
    }

    protected Intent createPlayingIntent(RadioDetails radioDetails, int operation) {
        Intent intent = new Intent("com.statichiss.recordio.PlayerService");

        if (radioDetails != null) {
            intent.putExtra(getString(R.string.radio_details_key), radioDetails);
        }

        intent.putExtra(getString(R.string.player_service_operation_key), operation);
        return intent;
    }

    public boolean alreadyPlaying() {
        RadioApplication radioApplication = (RadioApplication) getActivity().getApplication();
        MediaPlayer mediaPlayer = radioApplication.getMediaPlayer();
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    private void play(final RadioDetails radioDetails) {
        if (alreadyPlaying()) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            RadioApplication radioApplication = (RadioApplication) getActivity().getApplication();
            StringBuilder sb = new StringBuilder("Stop playing ")
                    .append(radioApplication.getPlayingType() == RadioApplication.PlayingStream ? radioApplication.getPlayingStation().getStationName() : radioApplication.getPlayingFileDetails().getName())
                    .append("?");

            builder.setMessage(sb.toString())
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            PlayerService.sendWakefulWork(getActivity().getApplicationContext(), createPlayingIntent(null, RadioApplication.StopPlaying));
                            PlayerService.sendWakefulWork(getActivity().getApplicationContext(), createPlayingIntent(radioDetails, RadioApplication.StartPlayingRadio));
                            getActivity().findViewById(R.id.main_stop_playing_btn).setEnabled(true);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    });
            builder.create().show();

        } else {
            PlayerService.sendWakefulWork(getActivity().getApplicationContext(), createPlayingIntent(radioDetails, RadioApplication.StartPlayingRadio));
            getActivity().findViewById(R.id.main_stop_playing_btn).setEnabled(true);
        }
    }

    private void record(final RadioDetails radioDetails) {
        final RadioApplication radioApplication = (RadioApplication) getActivity().getApplication();
        if (RecorderService.alreadyRecording()) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            builder.setMessage("Stop recording current station?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Log.d(TAG, "Stopping recording");
                            // Set stop var
                            RecorderService.cancelRecording();
                            // Fire start intent
                            RecorderService.sendWakefulWork(getActivity().getApplicationContext(), createRecordingIntent(radioDetails));

                            radioApplication.setRecordingStatus(getString(R.string.recording_string) + " " + (radioDetails.getStationName() != null ? radioDetails.getStationName() : ""));
                            updateUI();
                            getActivity().findViewById(R.id.main_stop_recording_btn).setEnabled(true);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    });

            builder.create().show();

        } else {

            Log.d(TAG, "Starting recording");
            Intent intent = createRecordingIntent(radioDetails);
            RecorderService.sendWakefulWork(getActivity(), intent);

            radioApplication.setRecordingStatus(getString(R.string.recording_string) + " " + (radioDetails.getStationName() != null ? radioDetails.getStationName() : ""));
            updateUI();
            getActivity().findViewById(R.id.main_stop_recording_btn).setEnabled(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(this.updateStatusBroadcastReceiver);
        getActivity().unregisterReceiver(this.sendErrorBroadcastReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter statusIntentFilter = new IntentFilter();
        statusIntentFilter.addAction(getString(R.string.player_service_update_playing_key));
        getActivity().registerReceiver(this.updateStatusBroadcastReceiver, statusIntentFilter);

        IntentFilter errorIntentFilter = new IntentFilter();
        errorIntentFilter.addAction(getString(R.string.player_service_update_playing_error_key));
        getActivity().registerReceiver(this.sendErrorBroadcastReceiver, errorIntentFilter);

        mAudioManager.registerMediaButtonEventReceiver(mRemoteControlReceiver);

        if (alreadyPlaying()) {


            final RadioApplication radioApplication = (RadioApplication) getActivity().getApplication();

            if (radioApplication.getPlayingType() == RadioApplication.PlayingFile) {
                Log.d(TAG, "Playing file...");
                getActivity().findViewById(R.id.progress_layout).setVisibility(View.VISIBLE);

                final SeekBar seekProgress = (SeekBar) getActivity().findViewById(R.id.seek_progress);
                final TextView timeElapsed = (TextView) getActivity().findViewById(R.id.time_elapsed);
                final TextView timeRemaining = (TextView) getActivity().findViewById(R.id.time_remaining);
                final MediaPlayer mp = radioApplication.getMediaPlayer();
                final int duration = radioApplication.getPlayingFileDetails().getDuration();

                seekProgress.setVisibility(View.VISIBLE);
                seekProgress.setMax(duration);
                seekProgress.setProgress(mp.getCurrentPosition());

                seekProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            mp.seekTo(progress);
                            timeElapsed.setText(DateUtils.getHoursAndMinutes(progress));
                            timeRemaining.setText(DateUtils.getHoursAndMinutes(duration - progress));
                        }
                    }

                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                new UpdateProgressTask().execute();
            }
        }

        updateUI();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(getActivity(), stationContentUri, null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        adapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        adapter.swapCursor(null);
    }

    private class UpdateProgressTask extends AsyncTask<Void, Integer, Void> {

        RadioApplication radioApplication = (RadioApplication) getActivity().getApplication();
        MediaPlayer mp = radioApplication.getMediaPlayer();
        int duration = radioApplication.getPlayingFileDetails().getDuration();
        SeekBar seekProgress = (SeekBar) getActivity().findViewById(R.id.seek_progress);
        TextView timeElapsed = (TextView) getActivity().findViewById(R.id.time_elapsed);
        TextView timeRemaining = (TextView) getActivity().findViewById(R.id.time_remaining);

        @Override
        protected Void doInBackground(Void... voids) {
            while (mp != null && mp.isPlaying() && mp.getCurrentPosition() < duration) {
                publishProgress(mp.getCurrentPosition());
            }
            // Send final msg to reset UI to non playing state?
            publishProgress(-1);
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... currentPosition) {
            if (currentPosition[0] > 0) {
                seekProgress.setProgress(currentPosition[0]);
                timeElapsed.setText(DateUtils.getHoursAndMinutes(currentPosition[0]));
                timeRemaining.setText(DateUtils.getHoursAndMinutes(duration - currentPosition[0]));
            }

            if (currentPosition[0] == -1) {
                //Reset UI
                getActivity().findViewById(R.id.progress_layout).setVisibility(View.GONE);
                updateUI();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAudioManager.unregisterMediaButtonEventReceiver(mRemoteControlReceiver);
    }

    public void goClick(View view) {
        final String source = ((EditText) getActivity().findViewById(R.id.txt_url)).getText().toString();
        Log.d(TAG, "url is: " + source);

        if (StringUtils.IsNullOrEmpty(source)) {
            showToast("Please supply a URL");
        } else {
            decideStreamOption(new RadioDetails(null, null, source));
        }
    }

    private void reportError(final String radioDetails, final String exception) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage("Sorry, cannot connect to this stream, would you like to send an error report so support can be added please?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        StringBuilder stringBuilder = new StringBuilder()
                                .append("Exception caught trying to play stream: ")
                                .append(exception)
                                .append("\n\n")
                                .append(radioDetails);

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("plain/text");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"statichiss@gmail.com"});
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Stream Error Report");
                        emailIntent.putExtra(Intent.EXTRA_TEXT, stringBuilder.toString());
                        startActivity(Intent.createChooser(emailIntent, "Send Error Report"));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .create().show();
    }

    private BroadcastReceiver updateStatusBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUI();
        }
    };

    private BroadcastReceiver sendErrorBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            String radioDetails = bundle.getString(getString(R.string.player_service_update_playing_error_radio_details));
            String exception = bundle.getString(getString(R.string.player_service_update_playing_error_exception));
            reportError(radioDetails, exception);
        }
    };

    private void showToast(String message) {
        Toast toast = Toast.makeText(getActivity().getApplicationContext(), message, Toast.LENGTH_SHORT);
        toast.show();
    }

}