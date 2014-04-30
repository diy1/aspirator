#/opt/local/bin/perl

# This script is to build a methods.txt that can be recognized by chord.
# The input: a file containing a list of .class
# Output: a methods.txt file

die "Usage: assembleMethods.pl <class_list_file>" unless @ARGV == 1;

#print "Commandline argument: $ARGV[0]\n"; 

open(CLASSLIST, $ARGV[0]) or die "Cannot open $ARGV[0]";
while ($line=<CLASSLIST>) {
  # print $line;
  # $slashIdx = rindex $line,"/";
  chomp $line;
  $line =~ s/\/([^\/]+).class$//g; # to remove the last component of the path, and record it  
  $classname = $1;
  # print "$line, $classname\n"; 
  
  system("javap -classpath $line -p -s $classname > ./tmp");

  parseJavapOutput();
  # print "Classname; $line, $classname\n";
  # last;
}
close(CLASSLIST);

sub parseJavapOutput
{
  open(TMP, "./tmp") or die "Cannot open ./tmp";
  $compiledFile = <TMP>; # first line, should be "Compiled from ..."
  $classInfo = <TMP>;
  $classInfo =~ /class (\S+)\s/;
  $className = $1;

  $buffer = ""; 
  while ($myline=<TMP>) {
    if ($myline =~ /^\s+Signature: \(/) {
      # a method
      chomp $myline;
      $myline =~ s/^\s+Signature: //g; 

      if ($buffer =~ / (\S+)\(/) {
        $methodName = $1;
        print "$methodName:$myline\@$className\n";
      }
    }
    $buffer = $myline; 
  } 
  close(TMP);
}
 
