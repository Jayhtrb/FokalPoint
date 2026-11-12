"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, CalendarDays, Check, CheckCircle2, ChevronDown, Clock3, Copy, CreditCard, LockKeyhole, MapPin, ShieldCheck, Smartphone, Wallet } from "lucide-react";

const creators:Record<string,{name:string;role:string;city:string;avatar:string;packages:{name:string;detail:string;price:number;duration:string}[]}> = {
  "arjun-menon": {name:"Arjun Menon",role:"Photographer · Videographer",city:"Hyderabad, India",avatar:"https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=500&q=85",packages:[{name:"Essential",detail:"4 hours · 1 photographer",price:25000,duration:"4 hours"},{name:"Premium",detail:"8 hours · 2 photographers",price:45000,duration:"8 hours"},{name:"Signature",detail:"Full day · Photo + film team",price:75000,duration:"Full day"}]},
  "wedding-lens": {name:"The Wedding Lens",role:"Wedding Photography & Films",city:"Hyderabad, India",avatar:"https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=500&q=85",packages:[{name:"Essential",detail:"4 hours · 1 photographer",price:35000,duration:"4 hours"},{name:"Premium",detail:"8 hours · Photo + film",price:55000,duration:"8 hours"},{name:"Signature",detail:"Full day · Complete team",price:85000,duration:"Full day"}]}
};

const money=(value:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:0}).format(value);

export default function BookingPage({params}:{params:Promise<{slug:string}>}) {
  const [slug,setSlug]=useState("arjun-menon");
  const [loaded,setLoaded]=useState(false);
  const [step,setStep]=useState(0);
  const [packageIndex,setPackageIndex]=useState(1);
  const [eventType,setEventType]=useState("Wedding");
  const [eventDate,setEventDate]=useState("");
  const [eventTime,setEventTime]=useState("09:00");
  const [eventLocation,setEventLocation]=useState("Hyderabad");
  const [customerName,setCustomerName]=useState("");
  const [email,setEmail]=useState("");
  const [phone,setPhone]=useState("");
  const [notes,setNotes]=useState("");
  const [method,setMethod]=useState("upi");
  const [bookingId,setBookingId]=useState("FP-2026-7824");
  const [copied,setCopied]=useState(false);

  useEffect(()=>{params.then(({slug:s})=>{setSlug(s);setLoaded(true);});},[params]);
  const creator=creators[slug]??creators["arjun-menon"];
  const selected=creator.packages[packageIndex];
  const tax=Math.round(selected.price*.18);
  const total=selected.price+tax;
  const canContinue=Boolean(eventDate&&eventLocation.trim()&&customerName.trim()&&email.trim()&&phone.trim());
  const dateLabel=eventDate?new Date(eventDate+"T12:00:00").toLocaleDateString("en-IN",{day:"2-digit",month:"short",year:"numeric"}):"Date to be confirmed";

  const completeRequest=()=>{setBookingId("FP-"+new Date().getFullYear()+"-"+Math.floor(100000+Math.random()*899999));setStep(2);};

  return <main className="booking-page">
    <header className="booking-nav"><Link href="/" className="fp-brand"><span className="fp-mark">●</span>FokalPoint</Link><div className="booking-secure"><LockKeyhole size={14}/> Secure booking experience</div><Link href={"/creators/"+slug} className="booking-back"><ArrowLeft size={15}/> Creator profile</Link></header>
    <div className="booking-wrap">
      <div className="booking-intro"><span className="eyebrow">MAKE IT OFFICIAL</span><h1>Book your <em>moment.</em></h1><p>A few details, one clear price, and you're on your way.</p></div>
      <div className="booking-steps">
        {["Service & details","Review & payment","Request received"].map((label,i)=><div className={"booking-step "+(step===i?"current":step>i?"done":"")} key={label}><span>{step>i?<Check size={14}/>:i+1}</span><small>{label}</small></div>)}
      </div>
      {step<2?<div className="booking-layout">
        <section className="booking-main-card">
          {step===0?<>
            <div className="booking-card-heading"><div><span className="eyebrow">STEP 01 / 02</span><h2>Tell us about the project</h2><p>Choose a package and share a few details with your creator.</p></div></div>
            <div className="booking-creator-strip"><img src={creator.avatar} alt={creator.name}/><div><strong>{creator.name}</strong><span>{creator.role}</span><small><MapPin size={12}/>{creator.city}</small></div><span className="booking-verified"><CheckCircle2 size={13}/> Verified</span></div>
            <div className="booking-field-section"><h3>Choose your package</h3><div className="booking-package-list">{creator.packages.map((p,i)=><button type="button" key={p.name} onClick={()=>setPackageIndex(i)} className={"booking-package "+(packageIndex===i?"chosen":"")}><span className="booking-radio">{packageIndex===i&&<i/>}</span><span className="booking-package-copy"><strong>{p.name}</strong><small>{p.detail}</small></span><b>{money(p.price)}</b></button>)}</div></div>
            <div className="booking-form-grid">
              <label>Event type<select value={eventType} onChange={e=>setEventType(e.target.value)}><option>Wedding</option><option>Pre-wedding</option><option>Birthday</option><option>Corporate event</option><option>Portrait session</option><option>Other</option></select></label>
              <label>Event date<input type="date" min={new Date().toISOString().slice(0,10)} value={eventDate} onChange={e=>setEventDate(e.target.value)}/></label>
              <label>Start time<select value={eventTime} onChange={e=>setEventTime(e.target.value)}>{["07:00","08:00","09:00","10:00","11:00","12:00","13:00","14:00","15:00","16:00","17:00","18:00"].map(t=><option key={t} value={t}>{new Date("2020-01-01T"+t).toLocaleTimeString("en-IN",{hour:"numeric",minute:"2-digit"})}</option>)}</select></label>
              <label>Event location<input value={eventLocation} onChange={e=>setEventLocation(e.target.value)} placeholder="Venue, city or area"/></label>
            </div>
            <div className="booking-field-section"><h3>Your contact details</h3><div className="booking-form-grid"><label>Full name<input value={customerName} onChange={e=>setCustomerName(e.target.value)} placeholder="Your name"/></label><label>Email address<input type="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="you@example.com"/></label><label>Mobile number<input type="tel" value={phone} onChange={e=>setPhone(e.target.value)} placeholder="+91 98765 43210"/></label><label className="booking-full">A note for the creator <textarea rows={3} value={notes} onChange={e=>setNotes(e.target.value)} placeholder="Tell them about your vision, schedule or special requests…"/></label></div></div>
            <div className="booking-form-footer"><span><ShieldCheck size={15}/> Your details are only shared with this creator.</span><button className="fp-btn fp-btn-gold" disabled={!canContinue} onClick={()=>setStep(1)}>Review booking <ArrowRight size={16}/></button></div>
          </>:<>
            <div className="booking-card-heading"><div><span className="eyebrow">STEP 02 / 02</span><h2>Review your booking</h2><p>Give everything one last look before sending your request.</p></div></div>
            <div className="review-summary"><div className="review-summary-head"><div><span className="eyebrow">YOUR CREATOR</span><h3>{creator.name}</h3><p>{creator.role}</p></div><img src={creator.avatar} alt={creator.name}/></div><div className="review-line"><span>Selected package</span><strong>{selected.name}</strong></div><div className="review-line"><span>Event type</span><strong>{eventType}</strong></div><div className="review-line"><span>Date & time</span><strong>{dateLabel} · {new Date("2020-01-01T"+eventTime).toLocaleTimeString("en-IN",{hour:"numeric",minute:"2-digit"})}</strong></div><div className="review-line"><span>Location</span><strong>{eventLocation}</strong></div><div className="review-line"><span>Contact</span><strong>{customerName} · {phone}</strong></div>{notes&&<div className="review-note"><span>Your note</span><p>{notes}</p></div>}</div>
            <div className="booking-field-section"><h3>Preferred payment method</h3><p className="booking-muted">This is a checkout preview. No payment will be collected at this stage.</p><div className="payment-methods"><button className={method==="upi"?"active":""} onClick={()=>setMethod("upi")}><Smartphone size={19}/><span><strong>UPI</strong><small>GPay, PhonePe, Paytm</small></span><i/></button><button className={method==="card"?"active":""} onClick={()=>setMethod("card")}><CreditCard size={19}/><span><strong>Credit / Debit card</strong><small>Visa, Mastercard, RuPay</small></span><i/></button><button className={method==="netbanking"?"active":""} onClick={()=>setMethod("netbanking")}><Wallet size={19}/><span><strong>Net banking</strong><small>All major banks</small></span><i/></button></div></div>
            <div className="booking-form-footer"><button className="fp-btn fp-btn-light-outline" onClick={()=>setStep(0)}><ArrowLeft size={15}/> Edit details</button><button className="fp-btn fp-btn-gold" onClick={completeRequest}>Send booking request <ArrowRight size={16}/></button></div>
          </>}
        </section>
        <aside className="booking-summary-card"><span className="eyebrow">YOUR BOOKING</span><h3>Payment summary</h3><div className="summary-service"><div className="summary-service-icon"><CalendarDays size={19}/></div><div><strong>{selected.name} package</strong><small>{selected.duration} · {creator.name}</small></div></div><div className="summary-price-line"><span>Service amount</span><strong>{money(selected.price)}</strong></div><div className="summary-price-line"><span>GST (18%)</span><strong>{money(tax)}</strong></div><div className="summary-total"><span>Total estimate</span><strong>{money(total)}</strong></div><p className="summary-tax-note">Final pricing and applicable taxes will be confirmed by the creator before payment.</p><div className="summary-assurance"><ShieldCheck size={17}/><span><strong>Protected booking</strong><small>Your booking isn't confirmed until the creator accepts it.</small></span></div><div className="summary-assurance"><Clock3 size={17}/><span><strong>No surprise charges</strong><small>You'll see the final amount before checkout.</small></span></div></aside>
      </div>:<section className="booking-success"><div className="success-icon"><Check size={34}/></div><span className="eyebrow">REQUEST SENT</span><h2>Your moment is<br/><em>in motion.</em></h2><p>Your booking request has been prepared for {creator.name}. This preview does not send data to a server or process payment yet.</p><div className="success-details"><div><span>Request reference</span><strong>{bookingId} <button onClick={()=>{navigator.clipboard?.writeText(bookingId);setCopied(true)}} aria-label="Copy booking reference"><Copy size={14}/>{copied?"Copied":"Copy"}</button></strong></div><div><span>Creator</span><strong>{creator.name}</strong></div><div><span>Package</span><strong>{selected.name}</strong></div><div><span>Event date</span><strong>{dateLabel}</strong></div><div><span>Estimated total</span><strong>{money(total)}</strong></div></div><div className="success-notice"><ShieldCheck size={17}/><span>Demo state only. Supabase booking persistence, creator acceptance and payment gateway integration are the next implementation steps.</span></div><div className="success-actions"><Link href={"/creators/"+slug} className="fp-btn fp-btn-light-outline">Back to creator</Link><Link href="/" className="fp-btn fp-btn-gold">Explore more creators <ArrowRight size={15}/></Link></div></section>}
    </div>
    <footer className="booking-footer"><Link href="/" className="fp-brand"><span className="fp-mark">●</span>FokalPoint</Link><span>Every Moment in Focus.</span><small>Secure by design · Made for creative people</small></footer>
  </main>;
}
