<img style="float: right;" src="conifer_logo_sm.png">

# Conifer Quick Start Guide

Conifer is a configuration framework for websites built on the GUS WDK
platform.

It uses variables defined in hierarchical layers of YAML files to
populate configuration templates. The hierarchy allows you to define
default values at a high level and then optionally override them a
lower, more specific level.


This quick start guide uses examples that depend on EBRC file and
directory naming conventions which are use to derive conifer command line
arguments based on the hostname of the website being configured. If you
do not use EBRC naming conventions then you will need to supply all the
required conifer command line arguments manually.

### Install

Conifer must be installed in to your `gus_home`. Conifer is installed
from source as part of the WDK build but you can short circuit that long
process and install conifer singularly. This step is useful if you want
to do website configuration before you build the source code.

    conifer install integrate.toxodb.org

### Seed    

Your organization will have defined default values for most settings
needed to configure a website. Some settings can not be pre-defined and
will need to be set by you in a site-specific file. The `seed`
subcommand will generate a file of site-specific variables for you to
fill in.

    conifer seed integrate.toxodb.org

This generates a `conifer_site_vars.seed.yml` in your website's `etc`
directory. Follow the instructions returned by the seed command to copy
that file to `conifer_site_vars.yml` and assign appropriate values to
the enclosed variables. The format of the file is YAML. The generated
seed file has only the settings that have not been pre-defined - the
minimal set of required values you must supply. You can also override
any of the pre-defined settings in your `conifer_site_vars.yml` file if
you desire. See the UsersGuide for more information.

### Configure

Once you have conifer installed and a site-specific variable file
prepared you can configure your site.

    conifer configure integrate.toxodb.org
