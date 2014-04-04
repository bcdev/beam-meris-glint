; these are the  subroutines of the breadboard
@subs/cm_ws_to_gauss2d_nn.pro
@subs/ref_surf_conversion.pro
@subs/clean_up_viaz.pro
@subs/wv_ocean_meris_nn.pro
@subs/calc_trans.pro
@subs/temptorad.pro
@subs/date2doy.pro
@subs/sol_irad_37.pro

; these subroutines are just for displaying
@fubstuff/win.pro
@fubstuff/true_color_index.pro
@fubstuff/t_c_i.pro
@fubstuff/red.pro
@fubstuff/green.pro
@fubstuff/blue.pro
@fubstuff/fub_image.pro
@fubstuff/do_plots.pro
; and for reading dimap data
@fubstuff/xml_node_to_struct.pro
@fubstuff/dimap_data__define.pro

pro fub_glint_breadboard,cas
	
	save_data=0

	; define some image output
	png={name:"bla.png",resolution:[640,512]*2,order:1,screen:1}

	; the co-registered meris aatsr files
	; HERE MERIS IS MASTER AND AATSR IS SLAVE!
	f="data/demo_meris_aatsr_"+cas+".dim"

	; this is the fub object to read beams dimap data
	ao=obj_new("dimap_data",f)
	fi=ao -> get_info()
	
	; stride saves time :-)
	stride=[20,30]/10.
	stride=[2,2]
	stride=[4,4]*2
;	stride=[1,1]
	
	; cumbersome way to find the day of year to adjust the solar constant
	doy=date2doy(fi.GENERAL_PRODUCTION.PRODUCT_SCENE_RASTER_START_TIME)

	; solar irradiance on that day of year 
	soir=sol_irad_37(doy)
	
	; this is a local environment range in pixel units.
	; It will be used later, to reduce ambiguity effects.
	; the number "300" is quite empirical and should not
	; be hardcoded. If FR then envir=envir*4 
	xrange=round(500./stride[0])
	yrange=round(100./stride[1])
	
	
	
	; reading the necessary tie data
	lat  =  ao  -> get_tie_data_by_name("latitude",offset=offset,count=count,stride=stride,found=found)
	lon  =  ao  -> get_tie_data_by_name("longitude",offset=offset,count=count,stride=stride,found=found)
	suzm  =  ao  -> get_tie_data_by_name("sun_zenith",offset=offset,count=count,stride=stride,found=found)
	vizm  =  ao  -> get_tie_data_by_name("view_zenith",offset=offset,count=count,stride=stride,found=found)
	suam  =  ao  -> get_tie_data_by_name("sun_azimuth",offset=offset,count=count,stride=stride,found=found)
	viam  =  ao  -> get_tie_data_by_name("view_azimuth",offset=offset,count=count,stride=stride,found=found)
	suea  =  ao  -> get_data_by_name("sun_elev_nadir_S",offset=offset,count=count,stride=stride,found=found)
	viea  =  ao  -> get_data_by_name("view_elev_nadir_S",offset=offset,count=count,stride=stride,found=found)
	suaa  =  ao  -> get_data_by_name("sun_azimuth_nadir_S",offset=offset,count=count,stride=stride,found=found)
	viaa  =  ao  -> get_data_by_name("view_azimuth_nadir_S",offset=offset,count=count,stride=stride,found=found)
	zw    =  ao  -> get_tie_data_by_name("zonal_wind",offset=offset,count=count,stride=stride,found=found)
	mw    =  ao  ->  get_tie_data_by_name("merid_wind",offset=offset,count=count,stride=stride,found=found)
	wssp  =  sqrt(zw^2+mw^2)
	claa  =  ao  -> get_data_by_name("cloud_flags_nadir_S",offset=offset,count=count,stride=stride,found=found)
	
	
	; this removes tie point interpolation artefacts/errors in the azimuth of
	; AATSR , The commented lines were for plotting 
; ; ; 	startps,outname="bildchen/"+cas+"/"+cas+"viaa",ys=1,xs=2
; ; ; 	plot_line=round(fi.raster_dimensions.ncols/stride[1]/2)
; ; ; 	nadi_colu=round(fi.raster_dimensions.nrows/stride[1]/2)
; ; ; 	xxx=findgen(fi.raster_dimensions.nrows/stride[0])*stride[0]
; ; ; 	plot,xxx,viaa[*,plot_line],line=2,xr=[320,730], xstyl=1	$
; ; ; 			,xtitle="pixel number",psym=-4 $
; ; ; 			,ytitle="viewing azimuth",yr=minmax(viaa[*,plot_line])*1.1
	viaa  =  clean_up_viaz(viaa,viea)
; ; ; 	oplot,xxx,viaa[*,plot_line],col=red(),psym=-4
; ; ; 	endps
	; and MERIS
; ; ; 	startps,outname="bildchen/"+cas+"/"+cas+"viam",ys=1,xs=2
; ; ; 	plot,xxx,viam[*,plot_line],line=2,xr=[320,730], xstyl=1	$
; ; ; 			,xtitle="pixel number",psym=-4 $
; ; ; 			,ytitle="viewing azimuth",yr=[80,300]
	viam  =  clean_up_viaz(viam,vizm,/meris)
; ; ; 	oplot,xxx,viam[*,plot_line],col=red(),psym=-4
; ; ; 	endps

	; get rid of azimuth angle ambiguity 
	idx=where(viaa lt 0,cnt)
	if cnt gt 0 then viaa[idx]=viaa[idx]+360.
	idx=where(viam lt 0,cnt)
	if cnt gt 0 then viam[idx]=viam[idx]+360.
	
	; this is the ENVISAT-ESA definition of the azimuth difference
	azda  =  viaa - suaa
	azdm  =  viam - suam
	
	; get rid of some azimuth difference angle ambiguity 
	idx=where(azda gt 180.,cnt)
	if cnt gt 0 then azda[idx]=360.-azda[idx]
	idx=where(azdm gt 180.,cnt)
	if cnt gt 0 then azdm[idx]=360.-azdm[idx]
	idx=where(azda lt 0.,cnt)
	if cnt gt 0 then azda[idx]=-azda[idx]
	idx=where(azdm lt 0.,cnt)
	if cnt gt 0 then azdm[idx]=-azdm[idx]
	
	
	stop
	
	
	; reading the the needed data (radiances and BTs)
	; again: HERE MERIS IS MASTER AND AATSR IS SLAVE!
	;
	me14  = ao  -> get_data_by_name("radiance_14_M",offset=offset,count=count,stride=stride,found=found)
	me15  = ao  -> get_data_by_name("radiance_15_M",offset=offset,count=count,stride=stride,found=found)
	aa16  = ao  -> get_data_by_name("reflec_nadir_1600_S",offset=offset,count=count,stride=stride,found=found)
	aa37  = ao  -> get_data_by_name("btemp_nadir_0370_S",offset=offset,count=count,stride=stride,found=found)
	aa11  = ao  -> get_data_by_name("btemp_nadir_1100_S",offset=offset,count=count,stride=stride,found=found)
	aa12  = ao  -> get_data_by_name("btemp_nadir_1200_S",offset=offset,count=count,stride=stride,found=found)
	aa87  = ao  -> get_data_by_name("reflec_nadir_0870_S",offset=offset,count=count,stride=stride,found=found)
; ; ; 	
; Here starts the processing
; 0.	process solely cloud free non- saturated AATSR pixel 
	cf_idx=where((claa eq 0 or claa eq 4) and (viea gt 0) and (aa37 gt 270) ,cnt,complement=cl_idx)  
;	cf_idx=where(viea gt -1 ,cnt,complement=cl_idx)  ; should I use no cloud mask at all?
	if cnt eq 0 then begin
		print,"No cloud free pixel, I stop here"
		stop
		;return
	endif

; 1. 	the solar part of 3.7 
;
; 1.a	a simple linear regression
;	the coefficients may be hardcoded for AATSR
;	everything is made on the cloud free pixel only: cf_idx
	nn37=aa37*0.-1   ; nn37 will contain the "thermal part" 
	termpar=[4.91348,0.978489,1.37919]
	nn37[cf_idx]=termpar[0]+termpar[1]*aa11[cf_idx]+termpar[2]*(aa11[cf_idx]-aa12[cf_idx])

; 1.b	claculation of the transmission at 3.7 and 1.6 µm
; 1.b.1 First: calculation of the water vapor column, 
;	using the standard L2 ANN from FUB. 
	inp_nn=fltarr(5,cnt)
	inp_nn[0,*]=wssp[cf_idx]
	inp_nn[1,*]=cosd(azdm[cf_idx])*sind(vizm[cf_idx])
	inp_nn[2,*]=cosd(vizm[cf_idx])
	inp_nn[3,*]=cosd(suzm[cf_idx])
	inp_nn[4,*]=alog((me15[cf_idx] > 1e-4) / (me14[cf_idx] > 1e-4))
	dum=wv_ocean_meris_nn(transpose(inp_nn),index=minmax_idx,/no_warn)
	wv=me15*0.		
	wv[cf_idx]=2.8		; standard

;	the network gives valid values only, where the minimum and maximum
;	of the input is not exceeded. Use the minmax values from the nna file header!
	wv[cf_idx[minmax_idx]]=dum[minmax_idx[*]]

	

; 1.b.2 calculation of the transmissions
	tr37=wv*0.+1
	tr37[cf_idx]=calc_trans_37(90.-suea[cf_idx],90.-viea[cf_idx],wv=wv[cf_idx])
	tr16=wv*0.+1
	tr16[cf_idx]=calc_trans_16(90.-suea[cf_idx],90.-viea[cf_idx],wv=wv[cf_idx])
	aa16t=aa16/tr16
;

; 1.c   calculation of the radiances/reflectance from the brightness temperature
	ra37=aa37*0.-1.
	ra37[cf_idx]=temptorad(aa37[cf_idx])/soir
	rn37=nn37*0.-1.
	rn37[cf_idx]=temptorad(nn37[cf_idx])/soir

; 1.d   Calculate the difference between the measured and the pure thermal part:
;	the solar part rr37. Correct for transmission
;	and convert to corresponding units of AATSR and MERIS 

	rr37=(ra37-rn37)/tr37			; pure solar in normalized radiance 1/sr
	rr37a=rr37*!pi/cosd(90.-suea)*100	; the same, but in AATSR % units


; 1.e	simple additional cloud mask: where the difference between 
;	rr37a (the pure solar signal at 3.7 um) 
;	and aa16t / 0.79 (the reflectance at 1.6um, corrected for gas
;	absorption and refractive index) is larger than 2 (AATSR %)
;	there could be a thin cloud or a huge aerosol! 	
;	plot,rr37a[cf_idx],aa16t[cf_idx]/0.79,psym=3,yr=[0,20],xr=[0,20]
;	oplot,findgen(100)
;	dum_idx=where((aa16t[cf_idx]/0.79-rr37a[cf_idx]) gt 0   $ 
;		and (aa16t[cf_idx]/0.79-rr37a[cf_idx]) lt 2 )
	dum_idx=where((aa16t[cf_idx]/0.79-rr37a[cf_idx]) gt -2   $ 
		and (aa16t[cf_idx]/0.79-rr37a[cf_idx]) lt 2 )
	;dum_idx=where((aa16t[cf_idx]/0.79-rr37a[cf_idx]) gt 0)  
	cf_idx=cf_idx[dum_idx[*]]
;	oplot,rr37a[cf_idx],aa16t[cf_idx]/0.79,col=255,psym=3




; 2.	The geometrical conversion
; 2.a.  doing the geometrical conversion
; 2.a.0	define the necessary fields
;
;	rr89  is the result, the  black sky, black water specular reflectance
;	wsss  is the "effective windspeed" (not the real one)
;	ambi  is a flag showing some information about the ambiguity
;	noer  indicates, that no result was found "no ergebniss"
;
;	rr89 and wsss may have up to 2 different values in their 4 fields .
;	A. the solution per pixel is uniq. Then all 4 solutions 
;	   are identical
;	B. the solution per pixel is "twofold". Then  
;	   - the first element is the solution for the lower posible windspeed
;	   - the second element is the solution for the higher posible windspeed
;	   - the third element is the solution for the windspeed which is closer 
;	     to the ECMWF field
;	   - the forth element is the solution for the winspeed which is closer 
;	     to the median of the local surrounding area. If in the local environment
;	     is NO retrieved windspeed, the solution from ECMWF is taken
;	C. there is no solution		

	si=size(lon)
;	define the output fields
	rr89=fltarr(si[1],si[2],4)*0.-1.
	wsss=fltarr(si[1],si[2],4)*0.-1.
	ambi=bytarr(si[1],si[2])*0.+3b
	noer=bytarr(si[1],si[2])*0.+1b
;	refractive index of water at 3.7µm and 0.89µm
	nin=1.37
	nou=1.33	

;	going thru all cloud free pixel
	print,"Geometric conversion"
	for i=0L, n_elements(cf_idx)-1L do begin
;		calculating the column and line numbers ...
;		this is very idl specific
		xx= (cf_idx[i] mod si[1])
		yy= (cf_idx[i]-xx)/si[1] mod si[2]
		
; 2.a.1 do the magic geometric conversion
;	this needs meris like azimuth definition
		ref_mer=ref_surf_conversion(rr37[xx,yy],suzm[xx,yy],vizm[xx,yy]		$
				,180.-azda[xx,yy],180-azdm[xx,yy],nin,nou		$
				,nothing_found=nothing_found,ambiguity=ambiguity	$
				,effective_ws=effective_ws,/no_plot)
		ambi[xx,yy]=ambiguity
		noer[xx,yy]=nothing_found
		if not nothing_found then begin
;	ref_mer and effective_ws can have one or two elements, 
;	depending on abiguity. BC may implement this differently
			rr89[xx,yy,0:1]=ref_mer
			wsss[xx,yy,0:1]=effective_ws
		endif
		if ambiguity eq 0 then begin
;	if the solution was uniq, all 4 solutions [xx,yy,0:3] are equal
			rr89[xx,yy,2:3]=ref_mer
			wsss[xx,yy,2:3]=effective_ws
		endif
		print,i,n_elements(cf_idx)-1L,string(13b),format="($,2(x,i7.7),a)"
	endfor
	print,""

	print,"Ambiguity reduction"
; 2.b	Tackle the ambiguity
 	am_idx=where(ambi eq 1,amb_count)
 	for i=0L,amb_count-1L do begin
 		xx= (am_idx[i] mod si[1])[0]
 		yy= (am_idx[i]-xx)/si[1] mod si[2]

; 2.b.1	this is the first way to overcome the ambiguity:
;	----> take the ws which is nearest to ECMWF <-----
		dum=min(abs(wsss[xx,yy,0:1]-wssp[xx,yy]),minidx)
		rr89[xx,yy,2]=rr89[xx,yy,minidx]
		wsss[xx,yy,2]=wsss[xx,yy,minidx]
;	but ECMWF is not always good!


; 2.b.2	this is the second way to overcome the ambiguity:
;	----> take the ws which is nearest to local environment median <-----
 		xstart=(xx-xrange)>0
 		xendet=(xstart+xrange*2) < (si[1] -1L)
 		ystart=(yy-yrange)>0
 		yendet=(ystart+yrange*2) < (si[2] -1L)
 		locwss= reform(wsss[xstart:xendet,ystart:yendet,0])
 		locamb= ambi[xstart:xendet,ystart:yendet]
 		locnoe= noer[xstart:xendet,ystart:yendet]
 		locidx=where((locamb eq 0) and (locnoe eq 0), loc_count)
 		if loc_count gt 0 then begin
; 			medws=median(locwss[locidx])
			medws=mean(locwss[locidx])
; 	here I take the ws which is nearest to median in local environment
 			dum=min(abs(wsss[xx,yy,0:1]-medws),minidx)
 			rr89[xx,yy,3]=rr89[xx,yy,minidx]
 			wsss[xx,yy,3]=wsss[xx,yy,minidx]
 		endif else begin
; 	take the ws which is nearest to ECMWF
;	since there is no retrieved ws in local environmemt
			rr89[xx,yy,3]=rr89[xx,yy,2]
			wsss[xx,yy,3]=wsss[xx,yy,2]
			ambi[xx,yy]=ambi[xx,yy]+1b			
 		endelse
		print,i,n_elements(am_idx)-1L,string(13b),format="($,2(x,i6.6),a)"
 	endfor
	print,""
; 2.c.
;	Reduce the cloud_free_index (cf_idx) to cases where noer equals 0 
	dum_idx=where(noer[cf_idx] eq 0)
	cf_idx=cf_idx[dum_idx[*]]

; 
; ; ; 	; display something (not needed for processing)
	do_plots,fi,png,cas,lon,lat,viea,me14,aa16,aa37,wv,aa16t,rr37a,nn37,wsss,wssp,ambi,rr89,to_screen=1

	; close the dimap object
	obj_destroy,ao

	stop

	; crude save into dimap (the fields have been created before)
	if keyword_set(save_data) then begin
		pfad="data/demo_meris_aatsr_"+cas+".data/"
		if total(stride eq [1,1]) ne 2 then stop,"will save the data only in original resolution"
		for i=0,3 do begin
			openw,lun,pfad+string(i+1,form="('glint_',i1.1,'.img')") ,/get_lun
			writeu,lun,swap_endian(reform(rr89[*,*,i])>0,/swap_if_little)
			free_lun,lun
			openw,lun,pfad+string(i+1,form="('ws_',i1.1,'.img')") ,/get_lun
			writeu,lun,swap_endian(reform(wsss[*,*,i])>0,/swap_if_little)
			free_lun,lun
		endfor
	endif
end



pro doit

	if strlowcase(!version.os_family) eq "windows" then stop,"Stopped! Edit source and change all path delimiter!"
	cas=["MedSea","RedSea","MedSeadim","Hawai"]
	cas=["RedSea","MedSea","MedSeadim","Hawai"]
	cas=["MedSea"]
	for icas=0,n_elements(cas)-1 do fub_glint_breadboard,cas[icas]
end