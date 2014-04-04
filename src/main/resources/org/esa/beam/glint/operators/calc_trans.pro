 function calc_trans_37,th1,th2,wv=wv
	if keyword_set(wv) then wvfac=wv/2.7872 $; g/cm^2
		else wvfac=th1*0.+1. 
	tr=th1*0.+1.
	am=1./cosd(th1)+1./cosd(th2)
; 	cka=read_esft_new("subs/ck_flex_cd_AATSR_sfp1000_03700.00.and.5.ck",/deb)
; 	ckh=read_esft_new("subs/ck_flex_cd_AATSR_sfp1000_03700.00.h2o.5.ck",/deb)
	a_koeff=transpose((read_ascii("subs/ck_flex_cd_AATSR_sfp1000_03700.00.and.5.ck.koeff.d")).field1)
	h_koeff=transpose((read_ascii("subs/ck_flex_cd_AATSR_sfp1000_03700.00.h2o.5.ck.koeff.d")).field1)
	weight =(read_ascii("subs/ck_flex_cd_AATSR_sfp1000_03700.00.h2o.5.ck.weight.d")).field1
	for i=0l,n_elements(th1)-1l do $
		tr[i]=total(exp(-total(a_koeff+h_koeff*wvfac[i],2)*am[i])*weight)
	return,tr
end
function calc_trans_16,th1,th2,wv=wv
	if keyword_set(wv) then wvfac=wv/2.7872  $; g/cm^2
		else wvfac=th1*0.+1. 
	tr=th1*0.+1.
	am=1./cosd(th1)+1./cosd(th2)
; 	cka=read_esft_new("subs/ck_flex_cd_AATSR_sfp1000_01600.00.and.4.ck",/deb)
; 	ckh=read_esft_new("subs/ck_flex_cd_AATSR_sfp1000_01600.00.h2o.4.ck",/deb)
	a_koeff=transpose((read_ascii("subs/ck_flex_cd_AATSR_sfp1000_01600.00.and.4.ck.koeff.d")).field1)
	h_koeff=transpose((read_ascii("subs/ck_flex_cd_AATSR_sfp1000_01600.00.h2o.4.ck.koeff.d")).field1)
	weight =(read_ascii("subs/ck_flex_cd_AATSR_sfp1000_01600.00.h2o.4.ck.weight.d")).field1
	for i=0l,n_elements(th1)-1l do $
		tr[i]=total(exp(-total(a_koeff+h_koeff*wvfac[i],2)*am[i])*weight)
	return,tr
end


; pro convert_kdist_files_to_simple_ascii
; f=["subs/ck_flex_cd_AATSR_sfp1000_03700.00.and.5.ck"	$
;   ,"subs/ck_flex_cd_AATSR_sfp1000_03700.00.h2o.5.ck"	$
;   ,"subs/ck_flex_cd_AATSR_sfp1000_01600.00.and.4.ck"	$
;   ,"subs/ck_flex_cd_AATSR_sfp1000_01600.00.h2o.4.ck"]
; for i=0,3 do begin
; 	ck=read_esft_new(f[i],/deb)
; 	openw,lun,f[i]+".koeff.d",/get,width=300
; 		printf,lun,transpose(ck.koeff)
; 	free_lun,lun
; 	openw,lun,f[i]+".weight.d",/get
; 		printf,lun,transpose(ck.weight)
; 	free_lun,lun
; endfor
; end

