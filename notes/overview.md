- draisine
- collateral
- czernobyl
- limits of control
- geotags 433
- solar buzzer
- imagine your echo
- rhizome
- lyapunov
- astloch
- window rsmp
- ...
- ...
- ...
- ...

-----

5 x 3
- lya        { graphics }
- ast        { scans    }
- text       { text     }
- sound      { sound    } ?
- collateral { video    }

# Collateral

## Collateral 521f9faf

Prepare input frames:

    avconv -i CollateralMurder_full.mp4 -ss 00:01:27 -t 00:01:50 -r 29.97 -f image2 collateral1-%d.png

- crop 430 x 430, horizontally centered (145), vertically offset (20)
- scale up to 1080 x 1080, sinc interp
- add noise (HSV: 'holdness' 8, 'value' 127)
- threshold 160
- begin frame 231 (1:27 == frame 1)
- numFrames = 1500, 1550

Assemble video:

    avconv -i 'collateral_vid/image_out/collat_521f9faf-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/collat_521f9faf.mp4

**Output new:**

cf. https://trac.ffmpeg.org/wiki/Encode/H.264

    avconv -i 'collateral_vid/image_out/collat_521f9faf-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/prothese_521f9faf.mp4

## Collateral 7a510609

Prepare input frames:

    avconv -i CollateralMurder_full.mp4 -ss 00:06:13 -t 00:01:05 -r 29.97 -f image2 collateral2-%d.png

- stop time 00:07:17

Note: this has two more frames than 1550. Use `AssembleCollat7a510609.scala` to  create the video.

**Output new:**

Same source code.

## Collateral b3a70216

Prepare input frames:

    avconv -i CollateralMurder_full.mp4 -ss 00:37:02 -t 00:00:47 -r 29.97 -f image2 collateral3-%d.png

- 34:50 .. 35:28
- 37:02 ... 37:49
- stop time 00:37:49

Assemble video:

    avconv -i 'collateral_vid/image_out/collat_b3a70216-%d.png' -vcodec libxvid -r 25 -q 100 -pass 2 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/collat_b3a70216.mp4

**Output new:**

    avconv -i 'collateral_vid/image_out/collat_b3a70216-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/prothese_b3a70216.mp4

# Trunk

## Trunk ac0d8490

    avconv -i 'trunks_vid/image_out/trunk_ac0d8490-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/trunk_ac0d8490.mp4

**Output new:**

    avconv -i 'trunks_vid/image_out/trunk_ac0d8490-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/peripherie_ac0d8490.mp4

## Trunk c20b1a57

    avconv -i 'trunks_vid/image_out/trunk_c20b1a57-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/trunk_c20b1a57.mp4

**Output new:**

    avconv -i 'trunks_vid/image_out/trunk_c20b1a57-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/peripherie_c20b1a57.mp4

## Trunk 45bc013a

(Note the rotation)

    avconv -i 'trunks_vid/image_out/trunk_45bc013a-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,transpose=1,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/trunk_45bc013a.mp4

**Output new:**

    avconv -i 'trunks_vid/image_out/trunk_45bc013a-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "transpose=1,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/peripherie_45bc013a.mp4

# Lyapunov

## Lyapunov 1de991d8

Combines beginning and ending of total four minutes rendering. Use `AssembleLya1de991d8s.scala` to  create the video.

**Output new:**

Same source code.

## Lyapunov 5aeddbc9

    avconv -i 'lyapunov_vid/image_out/lya_5aeddbc9-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=8,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/lya_5aeddbc9.mp4

**Output new:**

    avconv -i 'lyapunov_vid/image_out/lya_5aeddbc9-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=8,fade=type=out:start_frame=1525:nb_frames=25" \
    -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/phase_5aeddbc9.mp4

## Lyapunov e224f03c

Note the limitation on the number of frames (`-vframes`)

    avconv -i 'lyapunov_vid/image_out/lya_e224f03c-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=8,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/lya_e224f03c.mp4

**Output new:**

    avconv -i 'lyapunov_vid/image_out/lya_e224f03c-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=8,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/phase_e224f03c.mp4

# Text

## Text 1ff54f0f

    avconv -i 'text_vid/image_out/text_1ff54f0f-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12" \
    -vframes 1550 -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/text_1ff54f0f.mp4

**Output new:**

    avconv -i 'text_vid/image_out/text_1ff54f0f-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/partikel_1ff54f0f.mp4

## Text 50982b9

    avconv -i 'text_vid/image_out/text_50982b9-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/text_50982b9.mp4

**Output new:**

    avconv -i 'text_vid/image_out/text_50982b9-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/partikel_50982b9.mp4

## Text 589db7b0

    avconv -i 'text_vid/image_out/text_589db7b0-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 \
    -vf "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -vb 6M -threads 0 -f mp4 videos/text_589db7b0.mp4

**Output new:**

    avconv -i 'text_vid/image_out/text_589db7b0-%d.png' -c:v libx264 -r 25 -preset veryslow -crf 22 \
    -s:v 1080x1080 -vf "fade=type=in:start_frame=0:nb_frames=12,fade=type=out:start_frame=1525:nb_frames=25" \
    -vframes 1550 -aspect 1:1 -bufsize 8000K -maxrate 60000K -f mp4 videos/partikel_589db7b0.mp4

