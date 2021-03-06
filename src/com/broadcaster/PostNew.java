package com.broadcaster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.broadcaster.fragment.TopicDialog;
import com.broadcaster.model.AttachObj;
import com.broadcaster.model.AttachObj.AttachmentInteractListener;
import com.broadcaster.model.PostObj;
import com.broadcaster.model.ResponseObj;
import com.broadcaster.task.TaskAttachmentDel;
import com.broadcaster.task.TaskAttachmentNew;
import com.broadcaster.task.TaskBase;
import com.broadcaster.task.TaskBase.TaskListener;
import com.broadcaster.task.TaskGetLocation;
import com.broadcaster.task.TaskGetTopics;
import com.broadcaster.task.TaskManager;
import com.broadcaster.task.TaskPostNew;
import com.broadcaster.util.Constants;
import com.broadcaster.util.Constants.MEDIA_TYPE;
import com.broadcaster.util.Constants.PROGRESS_TYPE;
import com.broadcaster.util.ImageUtil;
import com.broadcaster.util.PathUtil;
import com.broadcaster.util.Util;
import com.broadcaster.view.AudioCaptureButton;
import com.broadcaster.view.AudioCaptureButton.OnNewCaptureListener;

public class PostNew extends BaseDrawerActivity {
    private Uri fileUri;
    private EditText locationText; 
    protected Button attach;
    private Button attachImage;
    private Button attachVideo;
    private AudioCaptureButton attachAudio;
    private LinearLayout attachGroup;
    private static final String customTopicText = "[Add New Topic]";

    protected EditText postTitle;
    protected EditText postText;
    protected Spinner postTag;
    protected TextView postId;
    protected LinearLayout gallery;
    protected ArrayList<AttachObj> attachments;
    protected ArrayAdapter<String> postTagAdapter;
    protected List<String> postTagItems;
    protected Integer prevSelectedTopic;
    protected PostObj mPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachments = new ArrayList<AttachObj>();

        locationText = (EditText)findViewById(R.id.post_new_location);
        attach = (Button)findViewById(R.id.post_new_attach);
        attachImage = (Button)findViewById(R.id.post_new_attach_image);
        attachAudio = (AudioCaptureButton)findViewById(R.id.post_new_attach_audio);
        attachVideo = (Button)findViewById(R.id.post_new_attach_video);
        attachGroup = (LinearLayout)findViewById(R.id.post_new_attach_group);
        gallery = (LinearLayout)findViewById(R.id.post_new_attachments);
        postTitle = (EditText)findViewById(R.id.post_new_title);
        postText = (EditText)findViewById(R.id.post_new_text);
        postTag = (Spinner)findViewById(R.id.post_new_tag);
        postId = (TextView)findViewById(R.id.post_update_id);

        postTagItems = new ArrayList<String>();
        postTagAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, postTagItems);
        postTag.setAdapter(postTagAdapter);
        postTag.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                if (postTagItems.get(arg2).equals(customTopicText)) {
                    showCustsomTopicDialog();
                }
                else {
                    prevSelectedTopic = arg2;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) { }

        });
        attach.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (attachGroup.getVisibility() == View.GONE) {
                    attachGroup.setVisibility(View.VISIBLE);
                    attach.setBackground(getResources().getDrawable(R.drawable.ic_action_remove));
                }
                else {
                    hideAttach();
                }
            }
        });
        attachAudio.setOnNewCaptureListener(new OnNewCaptureListener() {
            @Override
            public void onNewCapture(Uri newRecording) {
                insertAudio(newRecording);
            }
        });

        OnClickListener openContext = new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                openContextMenu(arg0);
            }
        };

        refreshTopics("default");
        registerForContextMenu(attachImage);
        registerForContextMenu(attachVideo);
        attachVideo.setTag(Constants.REQUEST_TYPE.VIDEO);
        attachImage.setTag(Constants.REQUEST_TYPE.IMAGE);
        attachImage.setOnClickListener(openContext);
        attachVideo.setOnClickListener(openContext);
        initProgressElements();
        (new TaskManager(this))
        .addTask((new TaskGetLocation()).setCallback(new TaskListener() {
            @Override
            public void postExecute(TaskManager tm, ResponseObj response) {
                locationText.setText(pref.getRealLocation().name);
            }
        }))
        .addTask((new TaskGetTopics()).setCallback(new TaskListener() {
            @Override
            public void postExecute(TaskManager tm, ResponseObj response) {
                refreshTopics("default");
            }
        }))
        .setProgress(PROGRESS_TYPE.OVERLAY)
        .run();
    }

    public void refreshTopics(String selected) {
        postTagItems.clear();
        prevSelectedTopic = 0;
        String[] topics = pref.getAllTags().split(",");
        HashSet<String> topicSet = new HashSet<String>();
        List<String> myTopics = pref.getMyTopics();
        for (String topic : myTopics) {
            topicSet.add(topic);
            postTagItems.add(topic);
        }    
        for (String topic : topics) {
            if (!topicSet.contains(topic)) {
                postTagItems.add(topic);
            }
        }    
        postTagItems.add(customTopicText);

        for (int i=0; i<postTagItems.size(); i++) {
            if (postTagItems.get(i).equals(selected)) {
                prevSelectedTopic = i;
                break;
            }
        }

        postTagAdapter.notifyDataSetChanged();
        resetPrevTopic();
    }

    public void resetPrevTopic() {
        postTag.setSelection(prevSelectedTopic);
    }

    protected void showCustsomTopicDialog() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        TopicDialog customTopicFragment = new TopicDialog();

        // The device is using a large layout, so show the fragment as a dialog
        customTopicFragment.show(fragmentManager, "dialog");
    }

    protected void hideAttach() {
        attachGroup.setVisibility(View.GONE);
        attach.setBackground(getResources().getDrawable(R.drawable.ic_action_new));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_submit).setVisible(true);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_submit:
            if (isValidPost()) {
                mPost = constructNewPost(mPost);
                Queue<TaskBase> attachmentTasks = new LinkedList<TaskBase>();
                for(AttachObj attachment : attachments) {
                    switch(attachment.type) {
                    case DELETE:
                        attachmentTasks.add(new TaskAttachmentDel(mPost, attachment));
                        break;
                    default:
                        attachmentTasks.add(new TaskAttachmentNew(PostNew.this, mPost, attachment));
                        break;
                    }
                }

                (new TaskManager(PostNew.this))
                .addTask(getPostTask())
                .addTask(attachmentTasks)
                .setCallback(getSubmitCallback())
                .setProgress(PROGRESS_TYPE.OVERLAY)
                .run();
                hideKeyboard();
            }

            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean isValidPost() {
        if (postTitle.getText().length() == 0) {
            postTitle.setError("Please enter a title.");
            return false;
        }
        return true;
    }

    protected TaskBase getPostTask() {
        return new TaskPostNew(mPost);
    }

    protected TaskListener getSubmitCallback() {
        return new TaskListener() {
            @Override
            public void postExecute(TaskManager tm, ResponseObj response) {
                Intent intent = new Intent(PostNew.this, ListByParent.class);
                Integer postId = mPost.id;
                if (mPost.parentId != null) {
                    postId = mPost.parentId;
                }
                intent.putExtra("postId", postId);
                startActivity(intent);
                finish();
            }
        };
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        Object requestTag = v.getTag();
        if (requestTag != null) {
            Constants.REQUEST_TYPE request = Constants.REQUEST_TYPE.valueOf(requestTag.toString());
            switch (request) {
            case VIDEO:
                inflater.inflate(R.menu.video_menu, menu);
                break;
            case IMAGE:
                inflater.inflate(R.menu.image_menu, menu);
                break;
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_pick_image:
            Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(i, Constants.REQUEST_PICK_IMAGE);
            return true;
        case R.id.menu_take_image:
            if(isIntentAvailable(MediaStore.ACTION_IMAGE_CAPTURE)) {
                fileUri = PathUtil.getMediaPath(MEDIA_TYPE.IMAGE);
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(takePictureIntent, Constants.REQUEST_TAKE_IMAGE);
            }
            else {
                showError(this.toString(), "Camera not available");
            }
            return true;
        case R.id.menu_pick_video:
            Intent mediaChooser = new Intent(Intent.ACTION_GET_CONTENT);
            mediaChooser.setType("video/*"); //mediaChooser.setType("video/*, images/*");
            startActivityForResult(mediaChooser, Constants.REQUEST_PICK_VIDEO);
            return true;
        case R.id.menu_take_video:
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            fileUri = PathUtil.getMediaPath(MEDIA_TYPE.VIDEO);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri); // set the file name
            startActivityForResult(intent, Constants.REQUEST_RECORD_VIDEO);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
        case Constants.REQUEST_PICK_IMAGE:
            if (resultCode == RESULT_OK && data != null) {
                Uri selectedImage = data.getData();
                String[] filePathColumn = { MediaStore.Images.Media.DATA };
                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String picturePath = cursor.getString(columnIndex);
                cursor.close();
                insertImage(picturePath);
            }
            break;
        case Constants.REQUEST_PICK_VIDEO:
            if (resultCode == RESULT_OK && data != null) {
                Uri dataUri = data.getData();
                String[] filePathColumn = { MediaStore.Video.Media.DATA };
                Cursor cursor = getContentResolver().query(dataUri, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String dataPath = cursor.getString(columnIndex);
                cursor.close();
                insertVideo(dataPath);
            }
            break;
        case Constants.REQUEST_TAKE_IMAGE:
            if (resultCode == RESULT_OK) {
                insertImage(fileUri.getPath());
            }
            break;
        case Constants.REQUEST_RECORD_VIDEO:
            if (resultCode == RESULT_OK) {
                insertVideo(fileUri.getPath());
            }
            break;
        default:
            super.onActivityResult(requestCode, resultCode, data);
            break;
        }
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_post_new;
    }

    @Override
    public void showProgress(PROGRESS_TYPE type) {
        switch(type) {
        case OVERLAY:
            getActionBar().hide();
            break;
        default:
            break;
        }
        super.showProgress(type);
    }

    @Override
    public void hideProgress(PROGRESS_TYPE type) {
        switch(type) {
        case OVERLAY:
            getActionBar().show();
            break;
        default:
            break;
        }
        super.hideProgress(type);
    }

    private void insertAudio(final Uri audioFile) {
        attachments.add(new AttachObj(MEDIA_TYPE.AUDIO, audioFile.getPath()));
        final int attachmentIndex = attachments.size() - 1;
        hideAttach();

        AttachObj.renderAttachment(this, null, audioFile.getPath(), null, MEDIA_TYPE.AUDIO, R.drawable.sound, gallery, true, new AttachmentInteractListener() {
            @Override
            public void onOpen(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(audioFile, "video/*");
                startActivity(intent);
            }

            @Override
            public boolean onDelete(View v) {
                attachments.remove(attachmentIndex);
                removeAttachmentView(v);
                return true;
            }
        });
    }

    private void insertVideo(final String videoFile) {
        attachments.add(new AttachObj(MEDIA_TYPE.VIDEO, videoFile));
        final int attachmentIndex = attachments.size() - 1;
        Bitmap thumnail = ImageUtil.createVideoThumb(videoFile);
        hideAttach();

        AttachObj.renderAttachment(this, null, videoFile, null, MEDIA_TYPE.VIDEO, thumnail, gallery, true, new AttachmentInteractListener() {
            @Override
            public void onOpen(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + videoFile), "video/*");
                startActivity(intent);
            }

            @Override
            public boolean onDelete(View v) {
                attachments.remove(attachmentIndex);
                removeAttachmentView(v);
                return true;
            }
        });
    }

    private void insertImage(final String imageFile) {
        attachments.add(new AttachObj(MEDIA_TYPE.IMAGE, imageFile));
        final int attachmentIndex = attachments.size()-1;
        hideAttach();

        Bitmap thumnail = null;
        try {
            thumnail = ImageUtil.getThumbnailFromFile(imageFile, 200);
        } catch (IOException e) {
            Util.logError(this, e);
        }
        AttachObj.renderAttachment(this, null, imageFile, null, MEDIA_TYPE.IMAGE, thumnail, gallery, true, new AttachmentInteractListener() {
            @Override
            public void onOpen(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + imageFile), "image/*");
                startActivity(intent);
            }

            @Override
            public boolean onDelete(View v) {
                attachments.remove(attachmentIndex);
                removeAttachmentView(v);
                return true;
            }
        });
    }

    protected boolean isIntentAvailable(String action) {
        final Intent intent = new Intent(action);
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public PostObj constructNewPost(PostObj po) {
        if (po == null) {
            po = new PostObj();
        }
        po.title = postTitle.getText().toString();
        po.setText(postText.getText().toString());
        po.visibility = 0;
        po.tags = postTagItems.get(postTag.getSelectedItemPosition());
        po.setLocation(pref.getRealLocation());
        return po;
    }

    protected void removeAttachmentView(View v) {
        ((LinearLayout)v.getParent().getParent()).removeView((LinearLayout)v.getParent());
    }
}
