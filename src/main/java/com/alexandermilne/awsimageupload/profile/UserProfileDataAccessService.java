package com.alexandermilne.awsimageupload.profile;

import com.alexandermilne.awsimageupload.bucket.BucketName;
import com.alexandermilne.awsimageupload.datastore.UserDao;
import com.alexandermilne.awsimageupload.filestore.FileStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static org.apache.http.entity.ContentType.*;

@Service
public class UserProfileDataAccessService {

    private final UserDao userDao;
    public FileStore fileStore;

    @Autowired
    public UserProfileDataAccessService(@Qualifier("fakeDao") UserDao userDao,
                                        FileStore fileStore) {
        this.userDao = userDao;
        this.fileStore = fileStore;
    }


    public int addUserProfile(UserProfile userProfile) {
        System.out.println(" personId: " + userProfile.getUserProfileId() + " person name: " + userProfile.getUsername());
        UUID id = userProfile.getUserProfileId();
        if (id == null){
            return addUserProfile(null, userProfile);
        } else {
            return addUserProfile(id, userProfile);
        }
    }

    public int addUserProfile(UUID userProfileId,  UserProfile userProfile) {
        userProfileId = Optional.ofNullable(userProfileId)
                .orElse(UUID.randomUUID());
        //if (person.getId().equals(null)){
        return userDao.insertUserProfile(userProfileId, userProfile);
    }


    List<UserProfile> getUserProfiles() {
        return userDao.getUserProfiles();
    }

    public int uploadUserProfileImage(UUID userProfileId, MultipartFile file) {

        //Check file is not null
        if (file.isEmpty()) {
            throw new IllegalStateException("Cannot upload empty file [" + file.getSize() + "]");
        }

        //Check file is correct type
        //IMAGE_JPEG.getMineType()
        if (Arrays.asList(IMAGE_JPEG, IMAGE_PNG, IMAGE_GIF).contains(file.getContentType())) {
            throw new IllegalStateException("File must be jpg, Png, or Gif [" + file.getContentType() + "]");
        }

        //get the db user
        UserProfile user = getUserProfileOrThrow(userProfileId);


        Map<String, String> metadata = new HashMap<>();
        metadata.put("Content-Type", file.getContentType());
        metadata.put("Content-Length", String.valueOf(file.getSize()));

        //store image in AWS db
        String path = getPathFormat(user);
        String filename = String.format("%s-%s", file.getOriginalFilename(), UUID.randomUUID());
        try {
            fileStore.save(path, filename, Optional.of(metadata), file.getInputStream());
            user.setUserProfileImageLink(filename);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }


        return userDao.uploadUserProfileImage(userProfileId, file);
    }

    private String getPathFormat(UserProfile user) {
        return String.format("%s/%s", BucketName.PROFILE_IMAGE.getBucketName(), user.getUserProfileId());
    }

    private UserProfile getUserProfileOrThrow(UUID userProfileId) {
        return userDao
                .getUserProfiles()
                .stream()
                .filter(userProfile -> userProfile.getUserProfileId().equals(userProfileId))
                .findFirst().orElseThrow(() -> new IllegalStateException(String.format("User profile %s not found", userProfileId)));
    }


    public byte[] downloadUserProfileImage(UUID userProfileId) {
        UserProfile user = getUserProfileOrThrow(userProfileId);
        String path = getPathFormat(user);

        return user.getUserProfileImageLink()
                .map(key -> fileStore.download(path, key))
                .orElse(new byte[0]);
    }
}
