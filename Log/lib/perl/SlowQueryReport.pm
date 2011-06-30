package FgpUtil::Log::SlowQueryReport;

use strict;
use Time::Local;
my ($time_min, $time_max);

sub makeReport {

  my ($serverList, $logfileRegex, $parseLogRecord, $time_filter, $plotOutputFile, $sort_column, $logTailSize, $logDeathImmunity, $threshold, $debug) = @_;

  if ($debug) {
    foreach my $svr (@$serverList) { 
      print "server: $svr\n";
    }
    print "logfileRegex = \"$logfileRegex\"\n";
  }

  my (%earliest, %latest, %count);

  if ($time_filter) {
    ($time_min, $time_max) = split(/,\s*/, $time_filter);
    print "\nTime filter start: " . localtime($time_min) . " ($time_min)\n";
    print   "Time filter end:   " . localtime($time_max) . " ($time_max)\n" if $time_max;
    print "\n";
  }

  my $h;

  if ($plotOutputFile) {
    open(P, ">$plotOutputFile") || die "Can't open plot output file '$plotOutputFile'\n";
  }

  my $min_absolute_time = 1000000000000000;
  my $max_absolute_time = 0;

  foreach my $server (@$serverList) {
    print "files from server $server\n" if $debug;
    open REMOTE_LS, "ssh $server ls $logfileRegex |"
      or die "couldn't open ssh command to list files";
    while (my $logFileName = <REMOTE_LS> ) {
      chomp($logFileName);
      print "    $logFileName\n" if $debug;
      open LOGFILE, "ssh $server cat $logFileName |"
	  or die "couldn't open ssh command to cat logfile";

      while(<LOGFILE>) {
	my ($reject, $timestamp, $seconds, $name) = $parseLogRecord->($_);
	next if $reject;
	next if ($time_min && $timestamp < $time_min);
	next if ($time_max && $timestamp > $time_max);

        # update global (per-report) statistics
	$min_absolute_time = $timestamp if $timestamp < $min_absolute_time;  # the first time we have included
	$max_absolute_time = $timestamp if $timestamp > $max_absolute_time;  # the latest time we have included

	# update per-file statistics
	$earliest{$logFileName} = $timestamp if (!$earliest{$logFileName} || $timestamp < $earliest{$logFileName});
	$latest{$logFileName} = $timestamp if (!$latest{$logFileName} || $timestamp > $latest{$logFileName});
	$count{$logFileName}++;

        # update per-query statistics
	if (!$h->{$name}) {
	  $h->{$name} = [$name, 0, 0, 0, 0, 0, $server, $logFileName];
	}
	$h->{$name}->[1] += $seconds;      # total secs
	$h->{$name}->[2] += 1;             # count
	if ($seconds > $threshold) {
	  $h->{$name}->[3] += $seconds;    # total secs over threshold
	  $h->{$name}->[4] += 1;           # count over threshold
	}

	if ($seconds > $h->{$name}->[5]) { # slowest instance yet of this query name
	  $h->{$name}->[5] = $seconds; # max run-time
	  $h->{$name}->[6] = $server;  # server with max run-time
	  $h->{$name}->[7] = $logFileName; # logfile containing max run-time
	}

	# if we are generating a plot data file, spit out this data point
	if ($plotOutputFile) {
	  print P "$timestamp\t$seconds\t$name\n";
	}
      }
    }
  }

  close(P) if ($plotOutputFile);

  my @sorted = sort {$b->[$sort_column-1] <=> $a->[$sort_column-1]} values(%$h);


  # name total_secs count avg_secs total_secs_over count_over  worst_secs
  print sprintf("%3s %47s%12s%8s%10s%12s%8s%7s%25s%80s\n",('  #', 'Name','TotSecs','Count','AvgSecs','SlowSecs','Slow_#','Worst', 'Server', 'Log File'));

  my $rownum;
  foreach my $a (@sorted) {
    my $avg = $a->[1] / $a->[2];
    print sprintf("%3d %47s%12.2f%8d%10.2f%12.2f%8d%7.2f%25s%80s\n",++$rownum,($a->[0],$a->[1],$a->[2],$avg,$a->[3],$a->[4],$a->[5],$a->[6],$a->[7]));
  }

  print "\nActual time start: " . localtime($min_absolute_time) . " ($min_absolute_time)\n";
  print   "Actual time end:   " . localtime($max_absolute_time) . " ($max_absolute_time)\n\n";

  print "statistics by log file:\n";
  print sprintf("%7s %24s %24s %13s %60s\n", "queries", "--------earliest----", "---------latest-----", "page-requests", "-------------------------------file------------");

  my $pageRequestCount;
  foreach my $f (sort(keys %count)) {
    $pageRequestCount = "(unknown)";
    if ($f =~ /(\w\w.\w*.org)/) {
      my $server = $1;
      print "found server \"$server\" in file \"$f\"\n" if $debug;
      $pageRequestCount = sprintf("%13d", getPageViews($server, $earliest{$f}, $latest{$f}, $logTailSize, $logDeathImmunity));
    }
    print sprintf("%7d %24s %24s %13s %60s\n", $count{$f}, scalar(localtime($earliest{$f})), scalar(localtime($latest{$f})), $pageRequestCount, $f);
  }

}

sub getPageViews {
  my ($server, $startTime, $endTime, $logTailSize, $logDeathImmunity) = @_;

  die "endTime $endTime is less than startTime $startTime"
    if $endTime < $startTime;

  my $logTailSize = 200000 if !$logTailSize;
  open LOGFILE, "ssh $server tail -$logTailSize /var/log/httpd/$server/access_log |"
    or die "couldn't open ssh command to cat logfile";

  my $minTimestamp = 1000000000000000;
  my $maxTimestamp = -1;
  my $uniquePerPage = 'GET /gbrowse/tmp/\w+aa';
  my $genePageCount;

  while(<LOGFILE>) {
    m|\[(\d\d)/(\w\w\w)/(\d\d\d\d)\:(\d\d)\:(\d\d)\:(\d\d) ...... "(.*)"$|;
    my ($mday, $mon_str, $year, $hour, $min, $sec, $command) = ($1, $2, $3, $4, $5, $6, $7);
    my $months = {Jan=>0, Feb=>1, Mar=>2, Apr=>3, May=>4, Jun=>5, Jul=>6, Aug=>7, Sep=>8, Oct=>9, Nov=>10, Dec=>11};
    my $day_str = "$mday, $mon_str, $year";
    my $mon = $months->{$mon_str};
    my $timestamp = timelocal($sec,$min,$hour,$mday,$mon,$year);

    $minTimestamp = $timestamp if $timestamp < $minTimestamp;  # the first time we have included
    $maxTimestamp = $timestamp if $timestamp > $maxTimestamp;  # the latest time we have included

    $genePageCount++ if ($timestamp > $startTime && $timestamp < $endTime && $command =~ /$uniquePerPage/);
  }

  # check that log file covers entire period of interest
  # if this dies, consider setting a larger $logTailSize, or overriding with $logDeathImmunity
  die "access log (" . localtime($minTimestamp) . " to "
    . localtime($maxTimestamp) .  ") doesn't cover entire period of interest ("
      . localtime($startTime) . " to " . localtime($endTime) .  ")"
	if ($minTimestamp > $startTime || $maxTimestamp < $endTime)
           && !$logDeathImmunity;

  return $genePageCount;

}


1;
