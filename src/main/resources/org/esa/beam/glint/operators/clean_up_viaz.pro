function clean_up_viaz,viaz,viel_in,meris=meris
; this function cleans some artefacts in MERIS and AATSR
; viewing azimut, that comes from tie-point interpolation 
; (which is not aplicable for non-steady (at Nadir) functions!)
; the solution is a secondorder polynom left and right
; from the Nadir point. 
;
; Inputs:
;--------------
; viaz		viewing azimuth in degree
; viel  	viewing elevation (90° = nadir for AATSR)
;		or viewing zenith (0° = nadir for MERIS)
;		booth in degree
; meris 	keyword that switches between AATSR and MERIS
;
;Output:	
;--------------
; viaz		viewing azimuth
;		with a nice discontinuity at Nadir


	if keyword_set(meris) then begin
		viel=90.-viel_in
		lim=85
	endif else begin
		viel=viel_in
		lim=85
	endelse
	si =size(viaz)
	out=viaz
	for i=0,si[2]-1 do begin  ; going over all lines
		nidx=where(viel[*,i] eq 0,ncount)
		dum=min(abs(1.-cosd(90.-viel[*,i])),mitte)
		; mitte is the index of the point closest to Nadir 

		idx=where(viel[0:mitte,i] lt lim and viel[0:mitte,i] gt 1,count)
		if count gt 2 then begin
			par=poly_fit(viel[idx,i],viaz[idx,i],2)
			out[0:mitte,i]=par[0]+par[1]*viel[0:mitte,i]+par[2]*(viel[0:mitte,i])^2
		endif
		idx=mitte+where(viel[mitte:*,i] lt lim and viel[mitte:*,i] gt 1,count)
		if count gt 2 then begin
			par=poly_fit(viel[idx,i],viaz[idx,i],2)
			out[mitte:*,i]=par[0]+par[1]*viel[mitte:*,i]+par[2]*(viel[mitte:*,i])^2
		endif
		if ncount gt 0 then out[nidx,i]=0.
	endfor
	return,out
end
