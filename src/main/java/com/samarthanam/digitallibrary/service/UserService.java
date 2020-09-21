package com.samarthanam.digitallibrary.service;

import java.util.Optional;

import com.samarthanam.digitallibrary.dto.request.UserLoginRequestDto;
import com.samarthanam.digitallibrary.dto.response.UserLoginResponseDto;
import com.samarthanam.digitallibrary.model.UserLoginToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.samarthanam.digitallibrary.constant.ServiceConstants;
import com.samarthanam.digitallibrary.dto.VerifySignUpDto;
import com.samarthanam.digitallibrary.dto.request.UserSignupRequestDto;
import com.samarthanam.digitallibrary.dto.response.UserSignupResponseDto;
import com.samarthanam.digitallibrary.dto.response.VerifySignUpResponseDto;
import com.samarthanam.digitallibrary.entity.User;
import com.samarthanam.digitallibrary.exception.ConflictException;
import com.samarthanam.digitallibrary.exception.TokenCreationException;
import com.samarthanam.digitallibrary.exception.TokenExpiredException;
import com.samarthanam.digitallibrary.exception.TokenTemperedException;
import com.samarthanam.digitallibrary.exception.UnauthorizedException;
import com.samarthanam.digitallibrary.model.EmailVerificationToken;
import com.samarthanam.digitallibrary.repository.UserRepository;
import com.samarthanam.digitallibrary.util.UserUtil;

import static com.samarthanam.digitallibrary.enums.ServiceError.*;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;

    private final String salt;

    public UserService(final UserRepository userRepository,
                       final TokenService tokenService,
                       @Value("${password.salt}") final String salt) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.salt = salt;
    }

    public UserSignupResponseDto signUp(UserSignupRequestDto userSignupRequestDto) throws ConflictException, TokenCreationException {

        User user = findByEmailAddressOrMobileNumber(userSignupRequestDto.getEmailAddress(),
                userSignupRequestDto.getMobileNumber());
        if (user != null && user.isEmailVerified()) {
            throw new ConflictException(USER_ALREADY_EXIST);
        } else if (user != null && !user.isEmailVerified()) {
            updateExistingUser(userSignupRequestDto, user);
            userRepository.save(user);
        } else {
            user = createNewUser(userSignupRequestDto);
            userRepository.save(user);
        }
        EmailVerificationToken emailVerificationToken = new EmailVerificationToken(user.getUserSeqId());
        String token = tokenService.createJwtToken(emailVerificationToken);
        //TODO: service to send email sendEmailToUser(token)
        return new UserSignupResponseDto("Email has been sent to your registered email id, token: " + token);

    }

    public VerifySignUpResponseDto verifySignUp(VerifySignUpDto verifySignUpDto) throws TokenTemperedException, TokenExpiredException, UnauthorizedException {
        EmailVerificationToken emailVerificationToken = (EmailVerificationToken) tokenService.decodeJwtToken(verifySignUpDto.getToken(),
                EmailVerificationToken.class);
        Optional<User> optionalUser = userRepository.findById(emailVerificationToken.getUserSequenceId());
        if (!optionalUser.isPresent()) {
            throw new UnauthorizedException(RESOURCE_NOT_FOUND);
        }
        User user = optionalUser.get();
        user.setEmailVerified(true);
        userRepository.save(user);
        return new VerifySignUpResponseDto(ServiceConstants.VERIFICATION_STATUS_SUCCESS);
    }

    public UserLoginResponseDto login(UserLoginRequestDto userLoginRequestDto) throws TokenCreationException, UnauthorizedException {
        String encryptedPassword = UserUtil.encryptPassword(userLoginRequestDto.getPassword(), salt);
        User dbUser = userRepository.findByEmailAddress(userLoginRequestDto.getEmail());
        if (dbUser != null && encryptedPassword.equals(dbUser.getUserPassword())) {
            if (dbUser.isEmailVerified()) {
                UserLoginToken userLoginToken = new UserLoginToken(dbUser.getFirstName(), dbUser.getLastName(),
                        dbUser.getGender(), dbUser.getMobileNumber(), dbUser.getEmailAddress(), dbUser.getUserSeqId());
                String token = tokenService.createJwtToken(userLoginToken);
                return new UserLoginResponseDto(token);
            } else {
                throw new UnauthorizedException(USER_NOT_VERIFIED);
            }
        } else {
            throw new UnauthorizedException(CREDENTIAL_MISMATCH);
        }
    }

    private User findByEmailAddressOrMobileNumber(String emailAddress, String mobileNumber) {
        User dbUser = userRepository.findByEmailAddress(emailAddress);
        if (dbUser == null) {
            dbUser = userRepository.findByMobileNumber(mobileNumber);
        }
        return dbUser;
    }

    private void updateExistingUser(UserSignupRequestDto userSignupRequestDto, User existingUser) {
        String encryptedPassword = UserUtil.encryptPassword(userSignupRequestDto.getPassword(), salt);
        existingUser.setUserPassword(encryptedPassword);
        existingUser.setFirstName(userSignupRequestDto.getFirstName());
        existingUser.setLastName(userSignupRequestDto.getLastName());
        existingUser.setMobileNumber(userSignupRequestDto.getMobileNumber());
        existingUser.setEmailAddress(userSignupRequestDto.getEmailAddress());
        existingUser.setGender(userSignupRequestDto.getGender());
        existingUser.setUpdateDate(System.currentTimeMillis());
    }

    private User createNewUser(UserSignupRequestDto userSignupRequestDto) {
        String encryptedPassword = UserUtil.encryptPassword(userSignupRequestDto.getPassword(), salt);
        return buildUserFromRequestDto(userSignupRequestDto, encryptedPassword, System.currentTimeMillis());
    }

    private User buildUserFromRequestDto(UserSignupRequestDto userSignupRequestDto,
                                         String encryptedPassword,
                                         long createDate) {
        return User.builder()
                .userPassword(encryptedPassword)
                .firstName(userSignupRequestDto.getFirstName())
                .lastName(userSignupRequestDto.getLastName())
                .emailAddress(userSignupRequestDto.getEmailAddress())
                .mobileNumber(userSignupRequestDto.getMobileNumber())
                .gender(userSignupRequestDto.getGender())
                .emailVerified(false)
                .adminApproved(false)
                .createDate(createDate)
                .build();
    }
}
