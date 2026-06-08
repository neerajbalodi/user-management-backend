package com.syn.usermanagement.service;

import com.syn.usermanagement.entity.User;
import com.syn.usermanagement.exception.ResourceNotFoundException;
import com.syn.usermanagement.exception.EmailAlreadyExistsException;
import com.syn.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final UserMetricsService userMetricsService;

    public Page<User> getAllUsers(Pageable pageable) {
        logger.debug("FETCH_USERS | Page: {} | Size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<User> users = userRepository.findAll(pageable);

        logger.debug("FETCH_USERS_SUCCESS | Retrieved: {} | TotalElements: {}",
                users.getNumberOfElements(), users.getTotalElements());

        return users;
    }

    public User getUserById(Long id) {
        logger.debug("FETCH_USER | UserId: {}", id);

        return userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("USER_NOT_FOUND | UserId: {}", id);
                    return new ResourceNotFoundException("User not found with id: " + id);
                });
    }

    @Transactional
    public User createUser(User user) {
        logger.info("CREATE_USER_ATTEMPT | Email: {} | Name: {}", user.getEmail(), user.getName());

        if (userRepository.existsByEmail(user.getEmail())) {
            logger.warn("CREATE_USER_FAILED | Email: {} | Reason: Email already exists", user.getEmail());
            throw new EmailAlreadyExistsException("Email already exists: " + user.getEmail());
        }

        User savedUser = userRepository.save(user);

        logger.info("CREATE_USER_SUCCESS | UserId: {} | Email: {}", savedUser.getId(), savedUser.getEmail());
        userMetricsService.incrementUserCreated();
        userMetricsService.incrementActiveUsers();

        return savedUser;
    }

    @Transactional
    public User updateUser(Long id, User userDetails) {
        logger.info("UPDATE_USER_ATTEMPT | UserId: {} | NewEmail: {} | NewName: {}",
                id, userDetails.getEmail(), userDetails.getName());

        User user = getUserById(id);

        // Check if email is being changed and if it already exists
        if (!user.getEmail().equals(userDetails.getEmail()) &&
                userRepository.existsByEmail(userDetails.getEmail())) {
            logger.warn("UPDATE_USER_FAILED | UserId: {} | Reason: Email already exists | NewEmail: {}",
                    id, userDetails.getEmail());
            throw new EmailAlreadyExistsException("Email already exists: " + userDetails.getEmail());
        }

        String oldEmail = user.getEmail();
        String oldName = user.getName();

        user.setName(userDetails.getName());
        user.setEmail(userDetails.getEmail());
        user.setPassword(userDetails.getPassword());

        User updatedUser = userRepository.save(user);

        logger.info("UPDATE_USER_SUCCESS | UserId: {} | EmailChanged: {} | NameChanged: {}",
                id,
                !oldEmail.equals(updatedUser.getEmail()),
                !oldName.equals(updatedUser.getName()));
        userMetricsService.incrementUserUpdated();

        return updatedUser;
    }

    @Transactional
    public void deleteUser(Long id) {
        logger.info("DELETE_USER_ATTEMPT | UserId: {}", id);

        User user = getUserById(id);
        String email = user.getEmail();

        userRepository.delete(user);

        logger.info("DELETE_USER_SUCCESS | UserId: {} | Email: {}", id, email);
        userMetricsService.incrementUserDeleted();
        userMetricsService.decrementActiveUsers();
    }

    /**
     * Upload photo for a user
     */
    public User uploadUserPhoto(Long userId, MultipartFile file) throws IOException {
        logger.info("PHOTO_UPLOAD_ATTEMPT | UserId: {} | FileName: {} | Size: {} bytes",
                userId, file.getOriginalFilename(), file.getSize());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("PHOTO_UPLOAD_FAILED | UserId: {} | Reason: User not found", userId);
                    return new RuntimeException("User not found with id: " + userId);
                });

        // Delete old photo if exists
        if (user.getPhotoUrl() != null) {
            logger.debug("PHOTO_DELETE_OLD | UserId: {} | OldPhotoUrl: {}", userId, user.getPhotoUrl());
            s3Service.deleteFile(user.getPhotoUrl());
        }

        try {
            // Upload new photo to S3
            String photoUrl = s3Service.uploadFile(file, userId);

            // Update user with new photo URL
            user.setPhotoUrl(photoUrl);
            User savedUser = userRepository.save(user);

            logger.info("PHOTO_UPLOAD_SUCCESS | UserId: {} | PhotoUrl: {}", userId, photoUrl);
            userMetricsService.incrementPhotoUploadSuccess();

            return savedUser;

        } catch (Exception e) {
            logger.error("PHOTO_UPLOAD_ERROR | UserId: {} | FileName: {} | Error: {}",
                    userId, file.getOriginalFilename(), e.getMessage(), e);
            userMetricsService.incrementPhotoUploadError();
            throw e;
        }
    }

    /**
     * Delete user photo
     */
    public User deleteUserPhoto(Long userId) {
        logger.info("PHOTO_DELETE_ATTEMPT | UserId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("PHOTO_DELETE_FAILED | UserId: {} | Reason: User not found", userId);
                    return new RuntimeException("User not found with id: " + userId);
                });

        if (user.getPhotoUrl() != null) {
            String oldPhotoUrl = user.getPhotoUrl();

            try {
                s3Service.deleteFile(user.getPhotoUrl());
                user.setPhotoUrl(null);
                User savedUser = userRepository.save(user);

                logger.info("PHOTO_DELETE_SUCCESS | UserId: {} | DeletedPhotoUrl: {}", userId, oldPhotoUrl);

                return savedUser;

            } catch (Exception e) {
                logger.error("PHOTO_DELETE_ERROR | UserId: {} | PhotoUrl: {} | Error: {}",
                        userId, oldPhotoUrl, e.getMessage(), e);
                throw e;
            }
        }

        logger.debug("PHOTO_DELETE_SKIPPED | UserId: {} | Reason: No photo exists", userId);
        return user;
    }
}