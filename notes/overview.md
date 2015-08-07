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
- sound      { sound    }
- collateral { video    }

# Collateral

    avconv -i CollateralMurder_full.mp4 -ss 00:01:27 -t 00:01:50 -r 29.97 -f image2 collateral1-%d.png

- crop 430 x 430, horizontally centered (145), vertically offset (20)
- scale up to 1080 x 1080, sinc interp
- add noise (HSV: 'holdness' 8, 'value' 127)
- threshold 160
- begin frame 231 (1:27 == frame 1)
- numFrames = 1500, 1550

