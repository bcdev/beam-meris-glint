function sind,in
	return,sin(in*3.1415926/180.) 
end
function cosd,in
	return,cos(in*3.1415926/180.) 
end
function gauss2d_recall,zen,azi,p
	x=cosd(90.-zen)*sind(azi)
	y=cosd(90.-zen)*cosd(azi)
	xx=x-0.  ; this looks stupid, its for me to remember the mathematics behind
	yy=y-p[3]
	u=(xx/p[1])^2+(yy/p[2])^2
	z=p[0]*exp(-u/2)
	return,z
end
function gauss2d_recall_multi,zen,azi,p
	x=cosd(90.-zen)*sind(azi)
	y=cosd(90.-zen)*cosd(azi)
	xx=p[*,0]*0.+x
	yy=-p[*,3]+y
	u=(xx/p[*,1])^2+(yy/p[*,2])^2
	z=p[*,0]*exp(-u/2)
	return,reform(z)
end


function ref_surf_conversion,refin,sun,vie,aziin,aziou,nin,nou		$
		,nothing_found=nothing_found,ambiguity=ambiguity	$
		,effective_ws=effective_ws,no_plots=no_plots

;NOTE: This function is not at all ELEGANT nor FAST, 
; it purpose is SOLELY to illustrate the mathematics 
; and  to be used as a reference breadboard
;
;INPUTS:
; refin		reflectance measured by  aatsr
;		the unit is radiance/solar_constant = 1/sr  (as it is used in momo) 
; sun		sun zenith angle (degree)
; vie		viewing zenith angle (degree)
; aziin 	azimuth difference angle of AATSR
; aziou		azimuth difference angle of MERIS
; nin		real part of refractive index at 3.7 um (1.3?)
; nou		real part of refractive index at 0.88 um (1.3?)
;
;OUTPUTS:
; refout	reflectance converted to MERIS 
;		the unit is radiance/solar_constant = 1/sr  (as it is used in momo) 
;
;effective_ws	effectiv windspeed  [m/s]
;nothing_found	true if no result was found
;ambiguity 	true if two results have been found
; 
; 
;
; in the next 30 lines a small 1D-LUT is 
; dynamically generated. 
	
	; n_windspeed is the number of elements
	n_windspeed=151  
	
	; ws is the LUT dimension
	ws=findgen(n_windspeed)/(n_windspeed-1)*13.+1
	
	
	; first generate synthetical reflectances at the different ws 
	; for the given AATSR geometry
	
	rf_aatsr_sim=fltarr(n_windspeed)  	; "simulated" glint reflectance for aatsr:
						;  = the LUT
	if not keyword_set(no_plots) then $
	rf_meris_sim=fltarr(n_windspeed)  	; "simulated" glint reflectance for meris
						; rf_meris_sim is NOT needed for the conversion
						; , only for some ilustrative plottings! 
	
	; The AATSR glint reflectance
	; fills the mini-LUT
	if not keyword_set(no_plots) then begin
		for i_windspeed=0,n_windspeed-1 do begin
			inp=[ws[i_windspeed],nin,cosd(sun)]  				; generates input vector for NN_LUT
			par=cm_ws_to_gauss2d_nn(inp)					; recals gaussparameter from NN_LUT
			rf_aatsr_sim[i_windspeed]=gauss2d_recall(vie,aziin,par)		; calculates aatsr reflactance at vie,aziin
			inp=[ws[i_windspeed],nou,cosd(sun)]  				; not needed, just for plotting
			par=cm_ws_to_gauss2d_nn(inp)		  			; not needed, just for plotting
			rf_meris_sim[i_windspeed]=gauss2d_recall(vie,aziou,par)    	; not needed, just for plotting
		endfor
	endif else begin	; the same as above, but using "idl magical" multi elements to  increase speed
		inp=transpose([transpose(ws),transpose(ws*0+nin),transpose(ws*0+cosd(sun))])
		par=cm_ws_to_gauss2d_nn(inp)
		rf_aatsr_sim=gauss2d_recall_multi(vie,aziin,par)
	endelse
	
	; find a maximum acceptable difference for LUT search
	diff_acceptable=0.
	for i=0,n_windspeed-2 do begin
		dum=abs((rf_aatsr_sim[i]-rf_aatsr_sim[i+1]))
		if dum gt diff_acceptable then diff_acceptable=dum
	endfor  

	; the index of the maximum reflectance
	dum=max(rf_aatsr_sim,max_idx)
; end of LUT generation




; the next steps can be ambigious
; 1. case: 	there is a maximum in reflectance as a function of windspeed
;		Then there could (conjunctive!) two solutions (two windspeeds)
;		belong to the measured reflectance of aatsr!
; 2. case: 	there is no windspeed which could produce the AATSR refl. at the 
;		given geometry
; 3. case	else (the nice one)
	
	if max_idx ne 0 and max_idx ne (n_windspeed-1) then begin  ; this is case 1.
		effective_ws=fltarr(2)-999
		ref_meris=fltarr(2)-999
	
		; get the first possible windspeed
		dum1=min(abs(rf_aatsr_sim[0:max_idx+1]-refin),idx1)
		; check if its outside
		if idx1 eq 0 or idx1 eq  max_idx+1 then begin
			if dum1 le diff_acceptable then begin  ; its outside, but close enough
				effective_ws[0]=ws[idx1]
				inp=[ws[idx1],nou,cosd(sun)]
				par=cm_ws_to_gauss2d_nn(inp)		
				ref_meris[0]=gauss2d_recall(vie,aziou,par)  
			endif 
		endif else begin ; ok its not outside
			if dum1 le diff_acceptable then begin
				effective_ws[0]=ws[idx1]
				inp=[ws[idx1],nou,cosd(sun)]
				par=cm_ws_to_gauss2d_nn(inp)		
				ref_meris[0]=gauss2d_recall(vie,aziou,par)  
			endif
		endelse
	
		; get the second possible windspeed
		dum2=min(abs(rf_aatsr_sim[max_idx-1:*]-refin),idx2)
		; check if its outside
		if idx2 eq (max_idx-1) or idx2 eq  n_windspeed-1 then begin  
			if dum2 le diff_acceptable then begin   ; its outside, but close enough
				effective_ws[1]=ws[max_idx-1+idx2]
				inp=[ws[max_idx-1+idx2],nou,cosd(sun)]
				par=cm_ws_to_gauss2d_nn(inp)		
				ref_meris[1]=gauss2d_recall(vie,aziou,par)  
			endif
		endif else begin ; ok its not outside
			if dum2 le diff_acceptable then begin
				effective_ws[1]=ws[max_idx-1+idx2]
				inp=[ws[max_idx-1+idx2],nou,cosd(sun)]
				par=cm_ws_to_gauss2d_nn(inp)		
				ref_meris[1]=gauss2d_recall(vie,aziou,par)  
			endif
		endelse
		
	
	endif else begin; this is case 3, a monoton in- or decrease with windspeed
	
		ref_meris=-999.
		effective_ws=-999.
	
		dum=min(abs(rf_aatsr_sim-refin),idx)
		; check if its outside
		if idx eq 0 or idx eq  n_windspeed-1 then begin
			if dum le diff_acceptable then begin     ; its outside, but close enough
				effective_ws=ws[idx]
				inp=[ws[idx],nou,cosd(sun)]
				par=cm_ws_to_gauss2d_nn(inp)		
				ref_meris=gauss2d_recall(vie,aziou,par)  
			endif
		endif else begin ; ok its not outside
			if dum le diff_acceptable then begin
				effective_ws=ws[idx]
				inp=[ws[idx],nou,cosd(sun)]
				par=cm_ws_to_gauss2d_nn(inp)		
				ref_meris=gauss2d_recall(vie,aziou,par)  
			endif
		endelse
	endelse 
	
	; now check how many windspeeds have been found
	idx=where(ref_meris ge 0,count)
	;print,n_elements(ref_meris),count
	case count of 
	0: begin
		nothing_found=1b
		ambiguity=0b
	end
	1: begin
		nothing_found=0b
		ambiguity=0b
		ref_meris=ref_meris[idx]
		effective_ws=effective_ws[idx]
	end
	2: begin
		nothing_found=0b
		ambiguity=1b
	end
	else: stop, "this should not happen"
	endcase

	if not keyword_set(no_plots) then begin
		yellow=65535
		green=65280
		plot,ws,rf_meris_sim,yr=[0,max([rf_meris_sim,rf_aatsr_sim])] $
		,xtitle="effective windspeed", ytitle="refl. [1/sr]"
		xyouts,1,refin,"AATSR",col=yellow,charsize=1.5
		xyouts,1,ref_meris,"MERIS",col=green,charsize=1.5
		oplot,ws,rf_aatsr_sim,col=yellow
		oplot,ws,rf_meris_sim,col=green
		oplot,[0,20],[1,1]*refin,col=yellow
		if not nothing_found then begin
			for i=0,n_elements(effective_ws)-1 do begin
				oplot,[1,1]*effective_ws[i],[0,100]
				oplot,[0,20],[1,1]*ref_meris[i],col=green
				plots,effective_ws[i],refin,col=yellow,psym=4,syms=3
				plots,effective_ws[i],ref_meris[i],col=green,psym=6,syms=3
			endfor
		endif
	endif

	return,ref_meris
	

end


