"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { ArrowLeft, ArrowRight, CheckCircle2, LockKeyhole, Mail } from "lucide-react";
import { createSupabaseBrowserClient } from "../../lib/supabase/browser";

type Mode = "signin" | "signup";

export default function AuthPage() {
  const [mode, setMode] = useState<Mode>("signin");
  const [returnTo, setReturnTo] = useState("/");
  useEffect(() => { const value = new URLSearchParams(window.location.search).get("next") || "/"; setReturnTo(value.startsWith("/") && !value.startsWith("//") ? value : "/"); }, []);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const nextPath = () => returnTo;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError(""); setMessage("");
    const supabase = createSupabaseBrowserClient();
    if (!supabase) {
      setError("Account access is not connected yet. Please try again after FokalPoint's Supabase project is configured.");
      setBusy(false); return;
    }
    try {
      if (mode === "signup") {
        const { data, error: authError } = await supabase.auth.signUp({
          email: email.trim(),
          password,
          options: {
            data: { full_name: name.trim() },
            emailRedirectTo: window.location.origin + "/auth/callback?next=" + encodeURIComponent(nextPath()),
          },
        });
        if (authError) throw authError;
        if (!data.session) {
          setMessage("Your account is nearly ready. Check your inbox to confirm your email, then sign in.");
        } else {
          window.location.assign(nextPath());
        }
      } else {
        const { error: authError } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
        if (authError) throw authError;
        window.location.assign(nextPath());
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "We couldn't complete that request. Please try again.");
    } finally { setBusy(false); }
  }

  return <main className="auth-page">
    <header className="auth-nav"><Link href="/" className="fp-brand"><span className="fp-mark">●</span>FokalPoint</Link><Link href="/" className="booking-back"><ArrowLeft size={15}/> Back to discovery</Link></header>
    <section className="auth-card">
      <span className="eyebrow">YOUR CREATIVE JOURNEY</span>
      <h1>{mode === "signin" ? <>Welcome <em>back.</em></> : <>Make room for <em>moments.</em></>}</h1>
      <p className="auth-subtitle">{mode === "signin" ? "Sign in to manage your bookings and keep your plans in focus." : "Create an account to keep your bookings, conversations and favourite creators together."}</p>
      <div className="auth-tabs"><button className={mode==="signin"?"active":""} onClick={()=>{setMode("signin");setError("");setMessage("");}}>Sign in</button><button className={mode==="signup"?"active":""} onClick={()=>{setMode("signup");setError("");setMessage("");}}>Create account</button></div>
      <form onSubmit={submit} className="auth-form">
        {mode==="signup"&&<label>Full name<input required autoComplete="name" value={name} onChange={e=>setName(e.target.value)} placeholder="Your name"/></label>}
        <label>Email address<input required type="email" autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="you@example.com"/></label>
        <label>Password<input required type="password" minLength={8} autoComplete={mode==="signin"?"current-password":"new-password"} value={password} onChange={e=>setPassword(e.target.value)} placeholder="At least 8 characters"/></label>
        {error&&<div className="auth-alert error">{error}</div>}
        {message&&<div className="auth-alert success"><CheckCircle2 size={17}/>{message}</div>}
        <button className="fp-btn fp-btn-gold auth-submit" type="submit" disabled={busy}>{busy?"Please wait…":mode==="signin"?"Sign in securely":"Create your account"} <ArrowRight size={16}/></button>
      </form>
      <div className="auth-privacy"><LockKeyhole size={14}/> Your account is protected with Supabase Auth.</div>
      <div className="auth-guest"><span>Just browsing or booking once?</span><Link href={returnTo === "/" ? "/discover" : returnTo}>Continue without an account</Link></div>
    </section>
    <footer className="booking-footer"><Link href="/" className="fp-brand"><span className="fp-mark">●</span>FokalPoint</Link><span>Every Moment in Focus.</span><small>Secure by design · Made for creative people</small></footer>
  </main>;
}
