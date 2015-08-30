#!/bin/sh
avconv -i 'collateral_vid/image_out/collat_b3a70216-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/prothese_b3a70216.mp4

avconv -i 'trunks_vid/image_out/trunk_ac0d8490-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/peripherie_ac0d8490.mp4

avconv -i 'trunks_vid/image_out/trunk_c20b1a57-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/peripherie_c20b1a57.mp4

avconv -i 'trunks_vid/image_out/trunk_45bc013a-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "transpose=1,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/peripherie_45bc013a.mp4

avconv -i 'lyapunov_vid/image_out/lya_5aeddbc9-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=8,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/phase_5aeddbc9.mp4

avconv -i 'lyapunov_vid/image_out/lya_e224f03c-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=8,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/phase_e224f03c.mp4

avconv -i 'text_vid/image_out/text_1ff54f0f-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/partikel_1ff54f0f.mp4

avconv -i 'text_vid/image_out/text_50982b9-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/partikel_50982b9.mp4

avconv -i 'text_vid/image_out/text_589db7b0-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/partikel_589db7b0.mp4

