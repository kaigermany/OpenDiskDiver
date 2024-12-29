# OpenDiskDiver
Open toolbox for drive and file inspection and recovery

# Status
This project is currently in an early and unfinished state!
Guessed progress: 20%

# Features:
- A interactive GUI for Windows Users
- An alternative console-only implementation that is fully OS-independent
- A guided disk copy function

- read MBR/GPT-Partition tables
- read NTFS partitions (including compressed files)
- read FAT partitions (ExFAT is currently not supported)
- write raw disk dumps (.img files)
- write ZIP-compressed disk dumps (by split up the drive into smaller blocks)

## TODO list:
- Module System
- disk drive format parsers:
    - ExFAT
    - ext
- other parsers planned:
    - VDI
    - ZIP
    - ...

(All format implementations should be written in such a way that they may can handle dead data sectors or other errrors as good as possible.)
