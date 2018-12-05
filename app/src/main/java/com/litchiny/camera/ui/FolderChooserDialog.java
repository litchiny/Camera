package com.litchiny.camera.ui;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.lwyy.camera.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import com.litchiny.camera.PreferenceKeys;
import com.litchiny.camera.StorageUtils;

public class FolderChooserDialog extends DialogFragment {
    private static final String TAG = "FolderChooserFragment";
    private String chosen_folder;
    private File current_folder;
    private AlertDialog folder_dialog;
    private ListView list;

    private static class FileWrapper implements Comparable<FileWrapper> {
        private final File file;
        private final String override_name;
        private final int sort_order;

        FileWrapper(File file, String override_name, int sort_order) {
            this.file = file;
            this.override_name = override_name;
            this.sort_order = sort_order;
        }

        public String toString() {
            if (this.override_name != null) {
                return this.override_name;
            }
            return this.file.getName();
        }

        public int compareTo(@NonNull FileWrapper o) {
            if (this.sort_order < o.sort_order) {
                return -1;
            }
            if (this.sort_order > o.sort_order) {
                return 1;
            }
            return this.file.getName().toLowerCase(Locale.US).compareTo(o.getFile().getName().toLowerCase(Locale.US));
        }

        public boolean equals(Object o) {
            if (!(o instanceof FileWrapper)) {
                return false;
            }
            FileWrapper that = (FileWrapper) o;
            if (this.sort_order == that.sort_order) {
                return this.file.getName().toLowerCase(Locale.US).equals(that.getFile().getName().toLowerCase(Locale.US));
            }
            return false;
        }

        public int hashCode() {
            return this.file.getName().toLowerCase(Locale.US).hashCode();
        }

        File getFile() {
            return this.file;
        }
    }

    private static class NewFolderInputFilter implements InputFilter {
        private static final String disallowed = "|\\?*<\":>";

        private NewFolderInputFilter() {
        }

        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            for (int i = start; i < end; i++) {
                if (disallowed.indexOf(source.charAt(i)) != -1) {
                    return "";
                }
            }
            return null;
        }
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        File new_folder = StorageUtils.getImageFolder(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(PreferenceKeys.getSaveLocationPreferenceKey(), "OpenCamera"));
        this.list = new ListView(getActivity());
        this.list.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                FolderChooserDialog.this.refreshList(((FileWrapper) parent.getItemAtPosition(position)).getFile());
            }
        });
        this.folder_dialog = new Builder(getActivity()).setView(this.list).setPositiveButton(17039370, null).setNeutralButton(R.string.new_folder, null).setNegativeButton(17039360, null).create();
        this.folder_dialog.setOnShowListener(new OnShowListener() {
            public void onShow(DialogInterface dialog_interface) {
                FolderChooserDialog.this.folder_dialog.getButton(-1).setOnClickListener(new OnClickListener() {
                    public void onClick(View view) {
                        if (FolderChooserDialog.this.useFolder()) {
                            FolderChooserDialog.this.folder_dialog.dismiss();
                        }
                    }
                });
                FolderChooserDialog.this.folder_dialog.getButton(-3).setOnClickListener(new OnClickListener() {
                    public void onClick(View view) {
                        FolderChooserDialog.this.newFolder();
                    }
                });
            }
        });
        if (new_folder.exists() || !new_folder.mkdirs()) {
            refreshList(new_folder);
        } else {
            refreshList(new_folder);
        }
        if (!canWrite()) {
            refreshList(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
            if (this.current_folder == null) {
                refreshList(new File("/"));
            }
        }
        return this.folder_dialog;
    }

    private void refreshList(File new_folder) {
        int i = 0;
        if (new_folder != null) {
            File[] files = null;
            try {
                files = new_folder.listFiles();
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<FileWrapper> listed_files = new ArrayList();
            if (new_folder.getParentFile() != null) {
                listed_files.add(new FileWrapper(new_folder.getParentFile(), getResources().getString(R.string.parent_folder), 0));
            }
            File default_folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            if (!(default_folder.equals(new_folder) || default_folder.equals(new_folder.getParentFile()))) {
                listed_files.add(new FileWrapper(default_folder, null, 1));
            }
            if (files != null) {
                int length = files.length;
                while (i < length) {
                    File file = files[i];
                    if (file.isDirectory()) {
                        listed_files.add(new FileWrapper(file, null, 2));
                    }
                    i++;
                }
            }
            Collections.sort(listed_files);
            this.list.setAdapter(new ArrayAdapter(getActivity(), 17367043, listed_files));
            this.current_folder = new_folder;
            this.folder_dialog.setTitle(this.current_folder.getAbsolutePath());
        }
    }

    private boolean canWrite() {
        try {
            if (this.current_folder != null && this.current_folder.canWrite()) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean useFolder() {
        if (this.current_folder == null) {
            return false;
        }
        if (canWrite()) {
            File base_folder = StorageUtils.getBaseFolder();
            String new_save_location = this.current_folder.getAbsolutePath();
            if (this.current_folder.getParentFile() != null && this.current_folder.getParentFile().equals(base_folder)) {
                new_save_location = this.current_folder.getName();
            }
            this.chosen_folder = new_save_location;
            return true;
        }
        Toast.makeText(getActivity(), R.string.cant_write_folder, 0).show();
        return false;
    }

    public String getChosenFolder() {
        return this.chosen_folder;
    }

    private void newFolder() {
        if (this.current_folder != null) {
            if (canWrite()) {
                final EditText edit_text = new EditText(getActivity());
                edit_text.setSingleLine();
                edit_text.setFilters(new InputFilter[]{new NewFolderInputFilter()});
                new Builder(getActivity()).setTitle(R.string.enter_new_folder).setView(edit_text).setPositiveButton(17039370, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (edit_text.getText().length() != 0) {
                            try {
                                File new_folder = new File(FolderChooserDialog.this.current_folder.getAbsolutePath() + File.separator + edit_text.getText().toString());
                                if (new_folder.exists()) {
                                    Toast.makeText(FolderChooserDialog.this.getActivity(), R.string.folder_exists, Toast.LENGTH_SHORT).show();
                                } else if (new_folder.mkdirs()) {
                                    FolderChooserDialog.this.refreshList(FolderChooserDialog.this.current_folder);
                                } else {
                                    Toast.makeText(FolderChooserDialog.this.getActivity(), R.string.failed_create_folder, Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(FolderChooserDialog.this.getActivity(), R.string.failed_create_folder, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }).setNegativeButton(17039360, null).create().show();
                return;
            }
            Toast.makeText(getActivity(), R.string.cant_write_folder, Toast.LENGTH_SHORT).show();
        }
    }

    public void onResume() {
        super.onResume();
        refreshList(this.current_folder);
    }

    public File getCurrentFolder() {
        return this.current_folder;
    }
}
