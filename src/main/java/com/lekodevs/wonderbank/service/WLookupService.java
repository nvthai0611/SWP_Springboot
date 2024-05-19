package com.lekodevs.wonderbank.service;

import org.springframework.stereotype.Service;

import com.lekodevs.wonderbank.common.EAccountTypeLookup;

@Service
public class WLookupService {

	public EAccountTypeLookup[] retrieveAccountTypeLookup(){
		return EAccountTypeLookup.values();
	} 
}
