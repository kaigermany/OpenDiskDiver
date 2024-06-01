# OpenDiskDiver
Open toolbox for drive and file inspection and recovery

# Status
This project is currently in an early and unfinished state!
Guessed progress: 10%

# Supported Features:
Currently supported: MBR/GPT-Partition tables, NTFS (including compressed files)

## TODO list:
- Module System
- GUI/CLI
- disk drive format parsers:
    - FAT
    - ext
- other parsers planned:
    - VDI
    - ZIP
    - ...
- copy raw disk function

All format implementations should be written in such a way that they may can handle dead data sectors as good as possible.
