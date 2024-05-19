package com.lekodevs.wonderbank.service;

import java.util.*;
import java.util.stream.Collectors;

import org.hibernate.id.UUIDGenerator;
import org.hibernate.id.uuid.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.lekodevs.wonderbank.dao.IUserRepository;
import com.lekodevs.wonderbank.dao.IUserRoleRepository;
import com.lekodevs.wonderbank.dao.IVerificationCodeRepository;
import com.lekodevs.wonderbank.entity.Role;
import com.lekodevs.wonderbank.entity.User;
import com.lekodevs.wonderbank.entity.UserRole;
import com.lekodevs.wonderbank.entity.WEntity;
import com.lekodevs.wonderbank.entity.WVerificationCode;
import com.lekodevs.wonderbank.entity.dto.UserRegisterRequestDTO;
import com.lekodevs.wonderbank.entity.param.UserRoleRequestParam;
import com.lekodevs.wonderbank.security.SecurityPrincipal;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class WUserService implements UserDetailsService {
	private static final Logger LOG = LoggerFactory.getLogger(WUserService.class);
	@Value("${app.base_url}")
	private String BASE_URL;

	@Autowired
	private IUserRepository userRepository;

	@Autowired
	private IUserRoleRepository userRoleRepository;

	@Autowired
	private IVerificationCodeRepository verificationCodeRepository;

	@Autowired
	WRoleService roleService;

	@Autowired
	WEntityService entityService;

	@Override
	public UserDetails loadUserByUsername(String username) {
		User user = userRepository.findByUsername(username);
		if (user != null) {
			List<UserRole> userRoles = userRoleRepository.findAllByUserId(user.getId());

			Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();

			userRoles.forEach(userRole -> {
				authorities.add(new SimpleGrantedAuthority(userRole.getRole().getName()));
			});

			UserDetails principal = new org.springframework.security.core.userdetails.User(user.getUsername(),
					user.getPassword(), authorities);

			return principal;
		}
		return null;
	}

	public User findByUsername(String username) {
		return userRepository.findByUsername(username);
	}

	public String createUser(UserRegisterRequestDTO userRequestDTO) {

		try {
			validateRegistrationDetails(userRequestDTO);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e.getMessage();
		}
		try {
			WEntity bankAccountHolder = (WEntity) dtoMapperRequestDtoToEntity(userRequestDTO);

			if (entityService.persistEntity(bankAccountHolder) != null) {
				User user = (User) dtoMapperRequestDtoToUser(userRequestDTO);

				user = userRepository.save(user);
				addUserRole(user, null);

				return "User successfully created.";
			}

			return "Failed to create user.";
		} catch (Exception e) {
			e.printStackTrace();
			return e.getCause().getMessage();
		}

	}

	private void validateRegistrationDetails(UserRegisterRequestDTO userRequestDTO) throws Exception {
		if (findByUsername(userRequestDTO.getUsername()) != null) {
			throw new Exception("Username already exists.");
		}

		if (entityService.findByEmail(userRequestDTO.getEmail()) != null) {
			throw new Exception("Email already exists.");
		}
		if (entityService.findByEntityNo(userRequestDTO.getEntityNo()) != null) {
			throw new Exception("Entity Number already exists.");
		}
		if (entityService.findByMobile(userRequestDTO.getMobile()) != null) {
			throw new Exception("Mobile already exists.");
		}
	}

	public User updateUser(UserRegisterRequestDTO userRequestDTO) {

		WEntity bankAccountHolder = (WEntity) dtoMapperRequestDtoToEntity(userRequestDTO);

		if (entityService.persistEntity(bankAccountHolder) != null) {
			User user = (User) dtoMapperRequestDtoToUser(userRequestDTO);

			user = userRepository.save(user);
			addUserRole(user, null);

			return user;
		}

		return null;
	}

	public User updatePassword(String password) {

		User user = SecurityPrincipal.getInstance().getLoggedInPrincipal();
		if (user != null) {
			user.setPassword(password);
			user = userRepository.save(user);
			return user;
		}

		return null;
	}

	public User resetPassword(String entityNo, String password) {

		User user = userRepository.findByEntityNo(entityNo);
		if (user != null) {
			user.setPassword(password);
			user = userRepository.save(user);
			return user;
		}

		return null;
	}

	public List<UserRole> findAllCurrentUserRole() {
		return userRoleRepository.findAllByUserId(SecurityPrincipal.getInstance().getLoggedInPrincipal().getId());

	}

	public Optional<User> findUserById(long id) {
		return userRepository.findById(id);
	}

	public void addUserRole(User user, Role role) {

		UserRole userRole = new UserRole();
		userRole.setUser(user);

		if (role == null) {
			role = roleService.findDefaultRole();
			userRole.setRole(role);
		}

		userRoleRepository.save(userRole);
	}

	public void addUserRoleByUsernameAndRoleName(UserRoleRequestParam param) {
		Role role = roleService.findRoleByName(param.getRole());
		if (role == null) {
			throw new RuntimeException("Role does not exist.");
		}
		User user = findByUsername(param.getUsername());
		if (user == null) {
			throw new RuntimeException("User does not exist.");
		}
		addUserRole(user, role);
	}

	private Object dtoMapperRequestDtoToUser(UserRegisterRequestDTO source) {
		User target = new User();
		target.setEntityNo(source.getEntityNo());
		target.setUsername(source.getUsername());
		target.setPassword(source.getPassword());

		return target;
	}

	private Object dtoMapperRequestDtoToEntity(UserRegisterRequestDTO source) {
		WEntity target = new WEntity();
		target.setEntityNo(source.getEntityNo());
		target.setEmail(source.getEmail());
		target.setFirstname(source.getFirstname());
		target.setLastname(source.getLastname());
		target.setIdNumber(source.getIdNumber());
		target.setStartDate(source.getStartDate());
		target.setEndDate(source.getEndDate());
		target.setMobile(source.getMobile());

		return target;
	}

	private Object dtoMapperRequestDtoToEntity(UserRegisterRequestDTO source, WEntity target) {
		if (target == null) {
			target = new WEntity();
		}

		if (source.getEntityNo() != null) {
			target.setEntityNo(source.getEntityNo());
		}
		if (source.getEmail() != null) {

			target.setEmail(source.getEmail());
		}

		if (source.getFirstname() != null) {

			target.setFirstname(source.getFirstname());
		}

		if (source.getLastname() != null) {

			target.setLastname(source.getLastname());
		}

		if (source.getMobile() != null) {

			target.setMobile(source.getMobile());
		}

		return target;
	}

	public void forgotPasswordOTP(String param, String notificationType) {
		WVerificationCode request = new WVerificationCode();
		request.setCreateDate(Calendar.getInstance().getTime());
		request.setDuration(60);

		if (notificationType.equalsIgnoreCase("Email")) {
			String code = UUID.randomUUID().toString();
			request.setCode(code);
			request.setEmail(param);
			WEntity entity = entityService.findByEmail(param);
			if (entity != null) {
				request.setEntityNo(entity.getEntityNo());

				verificationCodeRepository.save(request);
				sendEmail(request.getEmail(), request.getCode(), request.getEntityNo());
			}
		} else {
			String otp = String.format("%06d", new Random().nextInt(999999));
			request.setCode(otp);
			
			request.setMobile(param);
			WEntity entity = entityService.findByMobile(param);
			if (entity != null) {
				request.setEntityNo(entity.getEntityNo());

				verificationCodeRepository.save(request);
				sendSMS(request.getMobile(), request.getCode(), request.getEntityNo());
			}
		}

	}

	public WEntity updateEntity(String entityNo, UserRegisterRequestDTO request) {
		WEntity target = entityService.findByEntityNo(entityNo);
		if (target != null) {
			target = (WEntity) dtoMapperRequestDtoToEntity(request, target);
			entityService.persistEntity(target);
		}
		return target;
	}

	public void sendEmail(String emailTo, String token, String entityNo) {
		String verificationUrl = BASE_URL + "reset-password?entityNo=" + entityNo + "&token=" + token;
		
		//todo send email

	}
	
	public void sendSMS(String mobile, String token, String entityNo) {
		String verificationUrl = BASE_URL + "reset-password?entityNo=" + entityNo + "&token=" + token;
		
		String message = "Verification pin: " + token + "Verification link: " + verificationUrl;
		
		//todo send sms
	}

	public String getVerificationToken(String entityNo, String token) {
		WVerificationCode response = verificationCodeRepository.findByEntityNoAndCode(entityNo, token);
		if (response != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(response.getCreateDate());
			calendar.add(Calendar.SECOND, response.getDuration());
			Date currentTime = Calendar.getInstance().getTime();

			if (currentTime.after(calendar.getTime())) {
				return "Expired";
			}
			return "Valid";
		}

		return "Invalid";
	}
}
