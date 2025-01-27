package com.lekodevs.wonderbank.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lekodevs.wonderbank.common.EAccountTypeLookup;
import com.lekodevs.wonderbank.dao.IAccountTypeLookupRepository;
import com.lekodevs.wonderbank.dao.IEntityAccountRepository;
import com.lekodevs.wonderbank.entity.AccountTypeLookup;
import com.lekodevs.wonderbank.entity.EntityAccount;
import com.lekodevs.wonderbank.entity.WEntity;
import com.lekodevs.wonderbank.entity.dto.AccountBalanceResponseDTO;
import com.lekodevs.wonderbank.entity.dto.AccountCreateRequestDTO;
import com.lekodevs.wonderbank.entity.dto.PayRequestDTO;
import com.lekodevs.wonderbank.entity.dto.TransactRequestDTO;
import com.lekodevs.wonderbank.entity.dto.TransferRequestDTO;
import com.lekodevs.wonderbank.security.SecurityPrincipal;

import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;

@Service
@Transactional
public class WEntityAccountService {

	@Autowired
	IEntityAccountRepository entityAccountRepository;

	@Autowired
	IAccountTypeLookupRepository accountTypeLookupRepository;

	@Autowired
	WEntityService entityService;

	public String createAccount(AccountCreateRequestDTO requestDTO) {
		String entityNo = requestDTO.getEntityNo();
		if(entityNo == null || entityNo.isEmpty()) {
			entityNo = SecurityPrincipal.getInstance().getLoggedInPrincipal().getEntityNo();
		}
		WEntity accountHolder = entityService.findByEntityNo(entityNo);
		if(accountHolder == null) {
			return "Invalid details, please check and try again.";	
		}
		
		AccountTypeLookup lookup = accountTypeLookupRepository.findByName(requestDTO.getAccount());
		if(lookup == null) {
			return "Invalid account type, please check and try again.";	
		}
		
		List<EntityAccount> accountList = entityAccountRepository.findAllByAccountHolder(accountHolder)
				.stream()
				.filter(p -> p.getAccountType().getId() == lookup.getId())
				.collect(Collectors.toList());
		if(!accountList.isEmpty()) {
			return String.format("Entity already has a %s account.", lookup.getName());	
		}
		
		
			EntityAccount account = new EntityAccount();
			account.setAccountHolder(accountHolder);
			account.setAccountType(lookup);
			if (lookup.getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {

				if (requestDTO.getAmount() < lookup.getMinimumBalance()) {
					return String.format("%s Account minimum deposit expected is %s", lookup.getName(), lookup.getMinimumBalance());
				} else {
					account.setBalance(requestDTO.getAmount());
				}
			} else if (lookup.getId() == EAccountTypeLookup.CURRENT.getAccountTypeId()) {
				account.setOverDraftBalance(lookup.getMaximumOverdraftBalance());
			}

			entityAccountRepository.save(account);
		
		return "Account successfully created.";
	}

	public String withdraw(TransactRequestDTO requestDTO) {
		EntityAccount account = getAccountByName(requestDTO.getAccount());

		if (account.getAccountType().getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {
			double amount = account.getBalance() - requestDTO.getAmount();
			if (amount < account.getAccountType().getMinimumBalance()) {
				return "Insufficient funds in the specified account.";
			} else {
				account.setBalance(amount);
			}
			entityAccountRepository.save(account);
			return "Funds processed successfully.";

		} else if (account.getAccountType().getId() == EAccountTypeLookup.CURRENT.getAccountTypeId()) {

			if (account.getBalance() > requestDTO.getAmount()) {
				double amount = account.getBalance() - requestDTO.getAmount();
				account.setBalance(amount);
				return "Funds processed successfully.";
			} else {
				if (account.getOverDraftBalance() > requestDTO.getAmount()) {
					if (account.getBalance() > 0) {
						double deductFromBalance = requestDTO.getAmount() - account.getBalance();
						double deduct = account.getBalance() - deductFromBalance;
						account.setBalance(deduct);
						requestDTO.setAmount(deductFromBalance);
					}
					double amount = account.getOverDraftBalance() - requestDTO.getAmount();
					account.setOverDraftBalance(amount);
					entityAccountRepository.save(account);
					return "Funds processed successfully.";
				} else {
					double fullAccountBalance = account.getBalance() + account.getOverDraftBalance();

					if (fullAccountBalance > requestDTO.getAmount()) {

						if (account.getBalance() > 0) {
							double deductFromBalance = requestDTO.getAmount() - account.getBalance();
							double deduct = account.getBalance() - deductFromBalance;
							account.setBalance(deduct);
							requestDTO.setAmount(deductFromBalance);
						}

						if (account.getOverDraftBalance() > 0) {
							double deductFromBalance = requestDTO.getAmount() - account.getBalance();
							double deduct = account.getBalance() - deductFromBalance;
							account.setOverDraftBalance(deduct);
						}
						entityAccountRepository.save(account);
						return "Funds processed successfully.";
					}
				}
			}
		}
		return "Insufficient funds in the specified account.";
	}

	public String deposit(TransactRequestDTO requestDTO) {
		EntityAccount account = getAccountByName(requestDTO.getAccount());

		if (account.getAccountType().getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {
			double amount = account.getBalance() + requestDTO.getAmount();
			account.setBalance(amount);
			entityAccountRepository.save(account);
			return "Funds deposited successfully.";

		} else {
			if (account.getOverDraftBalance() < account.getAccountType().getMaximumOverdraftBalance()) {
				double overdraftDifference = account.getAccountType().getMaximumOverdraftBalance()
						- account.getOverDraftBalance();
				if (overdraftDifference >= requestDTO.getAmount()) {
					account.setOverDraftBalance(account.getOverDraftBalance() + requestDTO.getAmount());
				} else {
					double deductForOverdraft = requestDTO.getAmount() - overdraftDifference;
					account.setOverDraftBalance(account.getOverDraftBalance() + deductForOverdraft);

					double deductForBalance = requestDTO.getAmount() - deductForOverdraft;
					account.setBalance(account.getBalance() + deductForBalance);

				}
				entityAccountRepository.save(account);
				return "Funds deposited successfully.";
			} else {
				double amount = account.getBalance() + requestDTO.getAmount();
				account.setBalance(amount);
				entityAccountRepository.save(account);
				return "Funds deposited successfully.";
			}
		}
	}

	public String pay(PayRequestDTO requestDTO) {
		EntityAccount account = getAccountByAccountNumber(requestDTO.getAccountNumber()).orElse(null);

		if(account == null) {
			throw new ValidationException("Account does not exist");
		}
		
		if (account.getAccountType().getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {
			double amount = account.getBalance() + requestDTO.getAmount();
			account.setBalance(amount);
			entityAccountRepository.save(account);
			return "Funds paid successfully.";

		} else {
			if (account.getOverDraftBalance() < account.getAccountType().getMaximumOverdraftBalance()) {
				double overdraftDifference = account.getAccountType().getMaximumOverdraftBalance()
						- account.getOverDraftBalance();
				if (overdraftDifference >= requestDTO.getAmount()) {
					account.setOverDraftBalance(account.getOverDraftBalance() + requestDTO.getAmount());
				} else {
					double deductForOverdraft = requestDTO.getAmount() - overdraftDifference;
					account.setOverDraftBalance(account.getOverDraftBalance() + deductForOverdraft);

					double deductForBalance = requestDTO.getAmount() - deductForOverdraft;
					account.setBalance(account.getBalance() + deductForBalance);

				}
				entityAccountRepository.save(account);
				return "Funds paid successfully.";
			} else {
				double amount = account.getBalance() + requestDTO.getAmount();
				account.setBalance(amount);
				entityAccountRepository.save(account);
				return "Funds paid successfully.";
			}
		}
	}
	
	public String transfer(TransferRequestDTO requestDTO) {
		EntityAccount fromAccount = getAccountByName(requestDTO.getFromAccount());
		EntityAccount toAccount = getAccountByName(requestDTO.getToAccount());
		if (fromAccount.getAccountType().getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {
			double amount = fromAccount.getBalance() - requestDTO.getAmount();
			if (amount < fromAccount.getAccountType().getMinimumBalance()) {
				return "Insufficient funds in this account. Please deposit funds and try again.";
			} else {
				fromAccount.setBalance(amount);
				if(toAccount.getAccountType().getId() == EAccountTypeLookup.CURRENT.getAccountTypeId() && toAccount.getOverDraftBalance() == toAccount.getAccountType().getMaximumOverdraftBalance()) {
					toAccount.setBalance(toAccount.getBalance() + requestDTO.getAmount());
					
					entityAccountRepository.save(fromAccount);
					entityAccountRepository.save(toAccount);
					return "Funds transfered successfully.";
				}
				
				if (toAccount.getAccountType().getId() == EAccountTypeLookup.CURRENT.getAccountTypeId()) {
					if (toAccount.getOverDraftBalance() < toAccount.getAccountType().getMaximumOverdraftBalance()) {
						double deduct = toAccount.getAccountType().getMaximumOverdraftBalance()
								- toAccount.getOverDraftBalance();
						double deductForOverdraft = requestDTO.getAmount() - deduct;
						toAccount.setOverDraftBalance(toAccount.getOverDraftBalance() + deductForOverdraft);
						double deductForBalance = requestDTO.getAmount() - deductForOverdraft;
						toAccount.setBalance(toAccount.getBalance() + deductForBalance);
					}
				}
			}

			entityAccountRepository.save(fromAccount);
			entityAccountRepository.save(toAccount);
			return "Funds transfered successfully.";
		} else if (fromAccount.getAccountType().getId() == EAccountTypeLookup.CURRENT.getAccountTypeId()) {
			if (fromAccount.getBalance() >= requestDTO.getAmount()) {
				fromAccount.setBalance(fromAccount.getBalance() - requestDTO.getAmount());
				toAccount.setBalance(toAccount.getBalance() + requestDTO.getAmount());
			} else {
				double deductForBalance = requestDTO.getAmount() - fromAccount.getBalance();
				fromAccount.setBalance(fromAccount.getBalance() - deductForBalance);

				double deductForOverdraft = requestDTO.getAmount() - deductForBalance;
				fromAccount.setOverDraftBalance(fromAccount.getOverDraftBalance() - deductForOverdraft);

				toAccount.setBalance(toAccount.getBalance() + requestDTO.getAmount());
			}

			entityAccountRepository.save(fromAccount);
			entityAccountRepository.save(toAccount);
			return "Funds transfered successfully.";
		}

		return "Failed to transfer amount into account.";
	}  

	public AccountBalanceResponseDTO getAccountBalance() {
		EntityAccount account = getAccountByName(EAccountTypeLookup.CURRENT.name());
		AccountBalanceResponseDTO response = new AccountBalanceResponseDTO();
		response.setAccount(account.getAccountType().getName());
		response.setBalance(account.getBalance());
		if(account.getAccountType().getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {
			response.setOverdraft(null);
		}else {
			response.setOverdraft(account.getOverDraftBalance());
		}
		
		return response;
	}
	public AccountBalanceResponseDTO getAccountBalance(String accountName) {
		EntityAccount account = getAccountByName(accountName);
		AccountBalanceResponseDTO response = new AccountBalanceResponseDTO();
		response.setAccount(account.getAccountType().getName());
		response.setBalance(account.getBalance());
		if(account.getAccountType().getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {
			response.setOverdraft(null);
		}else {
			response.setOverdraft(account.getOverDraftBalance());
		}
		
		return response;
	}
	
	public List<AccountBalanceResponseDTO> getAccountBalanceList() {
		List<AccountBalanceResponseDTO> accountBalanceList = new ArrayList<>();
		for(EAccountTypeLookup eaccount: EAccountTypeLookup.values()) {
			EntityAccount account = getAccountByName(eaccount.name());
			AccountBalanceResponseDTO response = new AccountBalanceResponseDTO();
			response.setAccount(account.getAccountType().getName());
			response.setBalance(account.getBalance());
			if(account.getAccountType().getId() == EAccountTypeLookup.SAVINGS.getAccountTypeId()) {
				response.setOverdraft(null);
			}else {
				response.setOverdraft(account.getOverDraftBalance());
			}
			accountBalanceList.add(response);
		}
		
		return accountBalanceList;
	}

	private EntityAccount getAccountByName(String name) {
		WEntity accountHolder = entityService
				.findByEntityNo(SecurityPrincipal.getInstance().getLoggedInPrincipal().getEntityNo());

		return entityAccountRepository.findAllByAccountHolder(accountHolder).stream()
				.filter(account -> account.getAccountType().getName().equalsIgnoreCase(name))
				.findFirst()
				.orElse(null);
	}
	
	private Optional<EntityAccount> getAccountByAccountNumber(long accountNumber) {
		return entityAccountRepository.findById(accountNumber);
	}
}
